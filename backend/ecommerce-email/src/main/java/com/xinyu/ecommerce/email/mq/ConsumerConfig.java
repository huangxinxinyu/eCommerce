package com.xinyu.ecommerce.email.mq;

import com.xinyu.ecommerce.common.message.OrderNotifyMessage;
import com.xinyu.ecommerce.email.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ConsumerConfig {
    private final NotificationService notificationService;

    @Bean
    public Consumer<OrderNotifyMessage> orderNotifyConsumer() {
        return message -> {
            log.info("收到订单通知消息: orderNo={}, notifyType={}", message.getOrderNo(), message.getNotifyType());
            try {
                if (message.getNotifyType() == OrderNotifyMessage.TYPE_PAID) {
                    notificationService.sendPaidNotification(message);
                } else {
                    notificationService.sendClosedNotification(message);
                }
                log.info("订单通知处理完成: orderNo={}", message.getOrderNo());
            } catch (Exception e) {
                log.error("订单通知处理失败: orderNo={}", message.getOrderNo(), e);
                throw e;
            }
        };
    }
}
