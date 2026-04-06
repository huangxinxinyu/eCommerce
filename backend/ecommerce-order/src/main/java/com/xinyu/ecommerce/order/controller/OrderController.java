package com.xinyu.ecommerce.order.controller;

import com.xinyu.ecommerce.common.result.Result;
import com.xinyu.ecommerce.order.entity.Order;
import com.xinyu.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
}
