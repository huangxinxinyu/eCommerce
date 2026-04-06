package com.xinyu.ecommerce.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xinyu.ecommerce.common.constant.MqConstants;
import com.xinyu.ecommerce.common.message.PaySuccessMessage;
import com.xinyu.ecommerce.pay.dto.PayRequest;
import com.xinyu.ecommerce.pay.dto.PayResponse;
import com.xinyu.ecommerce.pay.entity.Payment;
import com.xinyu.ecommerce.pay.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {
    private final PaymentMapper paymentMapper;
    private final StreamBridge streamBridge;

    public PayResponse createPayment(PayRequest request) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getOrderNo, request.getOrderNo());
        Payment existPayment = paymentMapper.selectOne(wrapper);
        if (existPayment != null) {
            log.warn("支付记录已存在: orderNo={}", request.getOrderNo());
            PayResponse response = new PayResponse();
            response.setPayNo(existPayment.getPaymentNo());
            response.setOrderNo(existPayment.getOrderNo());
            response.setAmount(existPayment.getAmount());
            response.setPayStatus(existPayment.getStatus());
            return response;
        }

        String payNo = generatePayNo();
        Payment payment = new Payment();
        payment.setPaymentNo(payNo);
        payment.setOrderNo(request.getOrderNo());
        payment.setUserId(request.getUserId());
        payment.setAmount(request.getAmount());
        payment.setStatus(Payment.STATUS_PENDING);
        paymentMapper.insert(payment);

        log.info("支付创建成功: payNo={}, orderNo={}", payNo, request.getOrderNo());

        processPaymentCallback(payNo, request.getOrderNo(), request.getUserId(), request.getAmount());

        PayResponse response = new PayResponse();
        response.setPayNo(payNo);
        response.setOrderNo(request.getOrderNo());
        response.setAmount(request.getAmount());
        response.setPayStatus(Payment.STATUS_SUCCESS);
        return response;
    }

    private void processPaymentCallback(String payNo, String orderNo, Long userId, java.math.BigDecimal amount) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, payNo)
        );
        if (payment == null) {
            log.error("支付记录不存在: payNo={}", payNo);
            return;
        }

        payment.setStatus(Payment.STATUS_SUCCESS);
        payment.setPayTime(LocalDateTime.now());
        paymentMapper.updateById(payment);

        PaySuccessMessage message = new PaySuccessMessage();
        message.setPayNo(payNo);
        message.setOrderNo(orderNo);
        message.setUserId(userId);
        message.setPaidAmount(amount);
        message.setPayTime(System.currentTimeMillis());

        streamBridge.send("paySuccess-out-0", message);
        log.info("支付成功消息已发送: orderNo={}", orderNo);
    }

    public Payment getPaymentByNo(String payNo) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getPaymentNo, payNo);
        Payment payment = paymentMapper.selectOne(wrapper);
        if (payment == null) {
            throw new RuntimeException("支付记录不存在");
        }
        return payment;
    }

    private String generatePayNo() {
        return "PAY" + System.currentTimeMillis() + String.format("%06d", (int) (Math.random() * 1000000));
    }
}
