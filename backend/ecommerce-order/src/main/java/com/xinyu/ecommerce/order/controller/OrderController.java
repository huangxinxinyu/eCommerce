package com.xinyu.ecommerce.order.controller;

import com.xinyu.ecommerce.common.constant.OrderStatus;
import com.xinyu.ecommerce.common.result.Result;
import com.xinyu.ecommerce.order.entity.Order;
import com.xinyu.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @GetMapping("/query")
    public Result<Order> getOrder(@RequestParam String orderNo) {
        Order order = orderService.getOrderByNo(orderNo);
        return Result.success(order);
    }

    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancelOrder(@PathVariable String orderNo,
                                     @RequestHeader("X-User-Id") Long userId) {
        log.info("用户取消订单: orderNo={}, userId={}", orderNo, userId);
        Order order = orderService.getOrderByNo(orderNo);
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权取消该订单");
        }
        orderService.closeOrder(orderNo, OrderStatus.CANCELLED.getCode());
        return Result.success();
    }
}
