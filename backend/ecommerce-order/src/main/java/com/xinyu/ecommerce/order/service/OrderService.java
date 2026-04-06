package com.xinyu.ecommerce.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ecommerce.order.entity.Order;
import com.xinyu.ecommerce.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderMapper orderMapper;

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
        order.setStatus(0);
        orderMapper.insert(order);

        log.info("订单创建成功: orderNo={}, userId={}, productId={}", orderNo, userId, productId);
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
}
