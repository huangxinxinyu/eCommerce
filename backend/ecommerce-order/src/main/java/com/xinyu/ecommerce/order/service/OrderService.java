package com.xinyu.ecommerce.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ecommerce.common.constant.OrderStatus;
import com.xinyu.ecommerce.common.message.*;
import com.xinyu.ecommerce.order.entity.Order;
import com.xinyu.ecommerce.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;
    private final StreamBridge streamBridge;
    private final RedissonClient redissonClient;

    private static final long ORDER_EXPIRE_MINUTES = 15;

    public Order createOrder(String orderNo, Long userId, Long productId, String productName,
                            BigDecimal price, Integer quantity) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order existOrder = orderMapper.selectOne(wrapper);
        if (existOrder != null) {
            log.warn("订单已存在，跳过创建: orderNo={}", orderNo);
            return existOrder;
        }

        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductName(productName);
        order.setPrice(price);
        order.setQuantity(quantity);
        order.setTotalAmount(price.multiply(BigDecimal.valueOf(quantity)));
        order.setStatus(OrderStatus.PENDING.getCode());
        orderMapper.insert(order);

        log.info("订单创建成功: orderNo={}, userId={}, productId={}", orderNo, userId, productId);

        sendDelayCloseMessage(orderNo, userId);

        return order;
    }

    public Order getOrderByNo(String orderNo) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getOrderNo, orderNo);
        Order order = orderMapper.selectOne(wrapper);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return order;
    }

    public void handlePaySuccess(PaySuccessMessage message) {
        String lockKey = "order:pay:lock:" + message.getOrderNo();
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                log.warn("获取订单更新锁失败: orderNo={}", message.getOrderNo());
                return;
            }

            Order order = getOrderByNo(message.getOrderNo());
            if (order.getStatus() != OrderStatus.PENDING.getCode()) {
                log.warn("订单状态不是待支付，无法更新: orderNo={}, status={}",
                        message.getOrderNo(), order.getStatus());
                return;
            }

            order.setStatus(OrderStatus.PAID.getCode());
            order.setPayTime(LocalDateTime.now());
            orderMapper.updateById(order);

            log.info("订单支付成功: orderNo={}, payNo={}", message.getOrderNo(), message.getPayNo());

            sendPayNotifyMessage(order);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void closeOrder(String orderNo, int closeType) {
        String lockKey = "order:close:lock:" + orderNo;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                log.warn("获取关单锁失败: orderNo={}", orderNo);
                return;
            }

            Order order = getOrderByNo(orderNo);
            if (order.getStatus() != OrderStatus.PENDING.getCode()) {
                log.warn("订单状态不是待支付，无法关闭: orderNo={}, status={}",
                        orderNo, order.getStatus());
                return;
            }

            order.setStatus(closeType);
            order.setCloseTime(LocalDateTime.now());
            orderMapper.updateById(order);

            log.info("订单已关闭: orderNo={}, closeType={}", orderNo, closeType);

            sendStockRollbackMessage(order);
            sendCloseNotifyMessage(order, closeType);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void handleDelayClose(OrderDelayMessage message) {
        log.info("收到延迟关单消息: orderNo={}", message.getOrderNo());
        closeOrder(message.getOrderNo(), OrderStatus.CLOSED.getCode());
    }

    private void sendDelayCloseMessage(String orderNo, Long userId) {
        OrderDelayMessage msg = new OrderDelayMessage();
        msg.setOrderNo(orderNo);
        msg.setUserId(userId);
        msg.setTimestamp(System.currentTimeMillis() + ORDER_EXPIRE_MINUTES * 60 * 1000);

        try {
            streamBridge.send("orderDelay-out-0", msg);
            log.info("延迟关单消息发送成功: orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("延迟关单消息发送失败: orderNo={}", orderNo, e);
        }
    }

    private void sendStockRollbackMessage(Order order) {
        StockRollbackMessage msg = new StockRollbackMessage();
        msg.setOrderNo(order.getOrderNo());
        msg.setProductId(order.getProductId());
        msg.setUserId(order.getUserId());
        msg.setQuantity(order.getQuantity());

        try {
            streamBridge.send("stockRollback-out-0", msg);
            log.info("库存恢复消息发送成功: orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            log.error("库存恢复消息发送失败: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void sendPayNotifyMessage(Order order) {
        OrderNotifyMessage msg = new OrderNotifyMessage();
        msg.setOrderNo(order.getOrderNo());
        msg.setUserId(order.getUserId());
        msg.setAmount(order.getTotalAmount());
        msg.setNotifyType(OrderNotifyMessage.TYPE_PAID);

        try {
            streamBridge.send("orderNotify-out-0", msg);
            log.info("支付成功通知消息发送成功: orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            log.error("支付成功通知消息发送失败: orderNo={}", order.getOrderNo(), e);
        }
    }

    private void sendCloseNotifyMessage(Order order, int closeType) {
        OrderNotifyMessage msg = new OrderNotifyMessage();
        msg.setOrderNo(order.getOrderNo());
        msg.setUserId(order.getUserId());
        msg.setAmount(order.getTotalAmount());
        msg.setNotifyType(closeType == OrderStatus.CLOSED.getCode()
                ? OrderNotifyMessage.TYPE_CLOSED
                : OrderNotifyMessage.TYPE_CANCELLED);

        try {
            streamBridge.send("orderNotify-out-0", msg);
            log.info("订单关闭通知消息发送成功: orderNo={}", order.getOrderNo());
        } catch (Exception e) {
            log.error("订单关闭通知消息发送失败: orderNo={}", order.getOrderNo(), e);
        }
    }
}
