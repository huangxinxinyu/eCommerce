package com.xinyu.ecommerce.email.service;

import com.xinyu.ecommerce.common.message.OrderNotifyMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    public void sendPaidNotification(OrderNotifyMessage message) {
        log.info("[EMAIL] 发送邮件通知");
        log.info("[EMAIL] 收件人: user{}@example.com", message.getUserId());
        log.info("[EMAIL] 主题: 您的订单已支付成功");
        log.info("[EMAIL] 内容: 订单号 {}，金额 {} 元，支付时间 {}",
                message.getOrderNo(),
                message.getAmount(),
                java.time.LocalDateTime.now());
        log.info("[EMAIL] 通知发送完成");
    }

    public void sendClosedNotification(OrderNotifyMessage message) {
        log.info("[EMAIL] 发送邮件通知");
        log.info("[EMAIL] 收件人: user{}@example.com", message.getUserId());
        log.info("[EMAIL] 主题: 您的订单已关闭");
        if (message.getNotifyType() == OrderNotifyMessage.TYPE_CLOSED) {
            log.info("[EMAIL] 内容: 订单号 {} 因超时未支付已关闭，库存已恢复",
                    message.getOrderNo());
        } else {
            log.info("[EMAIL] 内容: 订单号 {} 已取消，库存已恢复",
                    message.getOrderNo());
        }
        log.info("[EMAIL] 通知发送完成");
    }
}
