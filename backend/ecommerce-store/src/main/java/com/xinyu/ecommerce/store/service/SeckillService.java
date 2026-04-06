package com.xinyu.ecommerce.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ecommerce.common.constant.MqConstants;
import com.xinyu.ecommerce.common.message.OrderCreateMessage;
import com.xinyu.ecommerce.store.entity.Product;
import com.xinyu.ecommerce.store.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final DefaultRedisScript<Long> seckillScript;
    private final DefaultRedisScript<Long> rollbackScript;
    private final ProductMapper productMapper;
    private final StreamBridge streamBridge;

    public SeckillService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("seckillScript") DefaultRedisScript<Long> seckillScript,
            @Qualifier("rollbackScript") DefaultRedisScript<Long> rollbackScript,
            ProductMapper productMapper,
            StreamBridge streamBridge) {
        this.redisTemplate = redisTemplate;
        this.seckillScript = seckillScript;
        this.rollbackScript = rollbackScript;
        this.productMapper = productMapper;
        this.streamBridge = streamBridge;
    }

    private static final String STOCK_KEY_PREFIX = "stock:seckill:";
    private static final String USER_BOUGHT_KEY_PREFIX = "user:bought:";

    public void initStock(Long productId, Integer stock) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, stock);
        log.info("初始化库存: productId={}, stock={}", productId, stock);
    }

    public void warmUpStockFromDb(Long productId, Integer stock) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, stock);
        log.info("从数据库回源预热库存: productId={}, stock={}", productId, stock);
    }

    public String seckillOrder(Long userId, Long productId, Integer quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String userBoughtKey = USER_BOUGHT_KEY_PREFIX + productId;

        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }

        Long result = redisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey, userBoughtKey),
                String.valueOf(quantity),
                String.valueOf(userId)
        );

        if (result == null) {
            throw new RuntimeException("系统错误");
        }

        if (result == -2) {
            log.warn("Redis 库存未初始化，尝试从数据库回源: productId={}", productId);
            warmUpStockFromDb(productId, product.getStock());
            result = redisTemplate.execute(
                    seckillScript,
                    Arrays.asList(stockKey, userBoughtKey),
                    String.valueOf(quantity),
                    String.valueOf(userId)
            );
            if (result == null || result <= 0) {
                throw new RuntimeException("库存回源后仍然失败");
            }
        }

        if (result == -1) {
            throw new RuntimeException("已购买过该商品");
        }

        if (result == 0) {
            throw new RuntimeException("库存不足");
        }

        String orderNo = generateOrderNo();
        OrderCreateMessage message = new OrderCreateMessage();
        message.setOrderNo(orderNo);
        message.setUserId(userId);
        message.setProductId(productId);
        message.setProductName(product.getName());
        message.setPrice(product.getPrice());
        message.setQuantity(quantity);
        message.setTimestamp(System.currentTimeMillis());

        streamBridge.send("seckill-order-out-0", message);
        log.info("秒杀下单成功，发送MQ消息: orderNo={}", orderNo);

        return orderNo;
    }

    public boolean rollback(String orderNo, Long userId, Long productId, Integer quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String userBoughtKey = USER_BOUGHT_KEY_PREFIX + productId;
        String rollbackKey = "stock:rollback:record:" + orderNo;

        Long result = redisTemplate.execute(
                rollbackScript,
                Arrays.asList(stockKey, userBoughtKey, rollbackKey),
                String.valueOf(quantity),
                String.valueOf(userId),
                orderNo
        );

        log.info("库存回滚: orderNo={}, userId={}, productId={}, result={}", orderNo, userId, productId, result);
        return result != null && result > 0;
    }

    public boolean confirmStockRollback(String orderNo, Long productId, Long userId, Integer quantity) {
        String rollbackKey = "stock:rollback:record:" + orderNo;
        String boughtKey = USER_BOUGHT_KEY_PREFIX + productId;

        Boolean exists = redisTemplate.hasKey(rollbackKey);
        if (!Boolean.TRUE.equals(exists)) {
            log.warn("库存恢复幂等标记不存在: orderNo={}", orderNo);
            return false;
        }

        Boolean hasBought = redisTemplate.opsForSet().isMember(boughtKey, userId.toString());
        if (Boolean.TRUE.equals(hasBought)) {
            log.warn("一人一单标记未清除: orderNo={}, userId={}", orderNo, userId);
            return false;
        }

        String stockKey = STOCK_KEY_PREFIX + productId;
        Boolean hasStock = redisTemplate.hasKey(stockKey);
        if (!Boolean.TRUE.equals(hasStock)) {
            log.warn("库存 Key 不存在: orderNo={}", orderNo);
            return false;
        }

        log.info("库存恢复确认成功: orderNo={}", orderNo);
        return true;
    }

    private String generateOrderNo() {
        return "SK" + System.currentTimeMillis() + String.format("%06d", (int) (Math.random() * 1000000));
    }
}
