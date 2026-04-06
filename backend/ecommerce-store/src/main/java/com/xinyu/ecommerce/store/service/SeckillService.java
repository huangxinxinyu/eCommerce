package com.xinyu.ecommerce.store.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ecommerce.common.constant.MqConstants;
import com.xinyu.ecommerce.common.message.OrderCreateMessage;
import com.xinyu.ecommerce.store.entity.Product;
import com.xinyu.ecommerce.store.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final RocketMQTemplate rocketMQTemplate;

    public SeckillService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("seckillScript") DefaultRedisScript<Long> seckillScript,
            @Qualifier("rollbackScript") DefaultRedisScript<Long> rollbackScript,
            ProductMapper productMapper,
            RocketMQTemplate rocketMQTemplate) {
        this.redisTemplate = redisTemplate;
        this.seckillScript = seckillScript;
        this.rollbackScript = rollbackScript;
        this.productMapper = productMapper;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    private static final String STOCK_KEY_PREFIX = "stock:seckill:";
    private static final String USER_BOUGHT_KEY_PREFIX = "user:bought:";

    public void initStock(Long productId, Integer stock) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(stockKey, stock);
        log.info("初始化库存: productId={}, stock={}", productId, stock);
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

        rocketMQTemplate.convertAndSend(MqConstants.TOPIC_SECKILL_ORDER, message);
        log.info("秒杀下单成功，发送MQ消息: orderNo={}", orderNo);

        return orderNo;
    }

    public boolean rollback(Long userId, Long productId, Integer quantity) {
        String stockKey = STOCK_KEY_PREFIX + productId;
        String userBoughtKey = USER_BOUGHT_KEY_PREFIX + productId;

        Long result = redisTemplate.execute(
                rollbackScript,
                Arrays.asList(stockKey, userBoughtKey),
                String.valueOf(quantity),
                String.valueOf(userId)
        );

        log.info("库存回滚: userId={}, productId={}, result={}", userId, productId, result);
        return result != null && result > 0;
    }

    private String generateOrderNo() {
        return "SK" + System.currentTimeMillis() + String.format("%06d", (int) (Math.random() * 1000000));
    }
}
