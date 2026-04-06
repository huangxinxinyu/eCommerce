package com.xinyu.ecommerce.order.mq;

import com.xinyu.ecommerce.common.message.OrderCreateMessage;
import com.xinyu.ecommerce.common.message.PaySuccessMessage;
import com.xinyu.ecommerce.common.message.OrderDelayMessage;
import com.xinyu.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ConsumerConfig {
    private final OrderService orderService;

    @Bean
    public Consumer<OrderCreateMessage> orderMqConsumer() {
        return message -> {
            log.info("收到订单创建消息: orderNo={}", message.getOrderNo());
            try {
                orderService.createOrder(
                        message.getOrderNo(),
                        message.getUserId(),
                        message.getProductId(),
                        message.getProductName(),
                        message.getPrice(),
                        message.getQuantity()
                );
                log.info("订单创建成功: orderNo={}", message.getOrderNo());
            } catch (Exception e) {
                log.error("订单创建失败: orderNo={}", message.getOrderNo(), e);
                throw e;
            }
        };
    }

    @Bean
    public Consumer<PaySuccessMessage> paySuccessConsumer() {
        return message -> {
            log.info("收到支付成功消息: orderNo={}, payNo={}", message.getOrderNo(), message.getPayNo());
            try {
                orderService.handlePaySuccess(message);
                log.info("支付成功处理完成: orderNo={}", message.getOrderNo());
            } catch (Exception e) {
                log.error("支付成功处理失败: orderNo={}", message.getOrderNo(), e);
                throw e;
            }
        };
    }

    @Bean
    public Consumer<OrderDelayMessage> orderDelayConsumer() {
        return message -> {
            log.info("收到延迟关单消息: orderNo={}", message.getOrderNo());
            try {
                orderService.handleDelayClose(message);
                log.info("延迟关单处理完成: orderNo={}", message.getOrderNo());
            } catch (Exception e) {
                log.error("延迟关单处理失败: orderNo={}", message.getOrderNo(), e);
                throw e;
            }
        };
    }
}
