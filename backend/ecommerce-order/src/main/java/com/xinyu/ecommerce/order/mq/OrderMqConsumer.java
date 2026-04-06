package com.xinyu.ecommerce.order.mq;

import com.xinyu.ecommerce.common.constant.MqConstants;
import com.xinyu.ecommerce.common.message.OrderCreateMessage;
import com.xinyu.ecommerce.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = MqConstants.TOPIC_SECKILL_ORDER,
        consumerGroup = MqConstants.GROUP_ID_ORDER
)
public class OrderMqConsumer implements RocketMQListener<OrderCreateMessage> {
    private final OrderService orderService;

    @Override
    public void onMessage(OrderCreateMessage message) {
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
    }
}
