package com.xinyu.ecommerce.pay.controller;

import com.xinyu.ecommerce.common.result.Result;
import com.xinyu.ecommerce.pay.dto.PayRequest;
import com.xinyu.ecommerce.pay.dto.PayResponse;
import com.xinyu.ecommerce.pay.entity.Payment;
import com.xinyu.ecommerce.pay.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {
    private final PayService payService;

    @PostMapping("/create")
    public Result<PayResponse> createPayment(@RequestBody PayRequest request) {
        log.info("创建支付请求: orderNo={}, userId={}, amount={}",
                request.getOrderNo(), request.getUserId(), request.getAmount());
        PayResponse response = payService.createPayment(request);
        return Result.success(response);
    }

    @GetMapping("/query/{payNo}")
    public Result<Payment> queryPayment(@PathVariable String payNo) {
        Payment payment = payService.getPaymentByNo(payNo);
        return Result.success(payment);
    }
}
