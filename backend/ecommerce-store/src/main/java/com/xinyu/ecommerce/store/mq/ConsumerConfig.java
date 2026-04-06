package com.xinyu.ecommerce.store.mq;

import com.xinyu.ecommerce.common.message.StockRollbackMessage;
import com.xinyu.ecommerce.store.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class ConsumerConfig {
    private final SeckillService seckillService;

    @Bean
    public Consumer<StockRollbackMessage> stockRollbackConsumer() {
        return message -> {
            log.info("收到库存恢复消息: orderNo={}, productId={}, userId={}, quantity={}",
                    message.getOrderNo(), message.getProductId(), message.getUserId(), message.getQuantity());
            try {
                boolean success = seckillService.rollback(
                        message.getOrderNo(),
                        message.getUserId(),
                        message.getProductId(),
                        message.getQuantity()
                );
                if (success) {
                    log.info("库存恢复成功: orderNo={}", message.getOrderNo());
                } else {
                    log.warn("库存恢复失败(可能已恢复): orderNo={}", message.getOrderNo());
                }
            } catch (Exception e) {
                log.error("库存恢复异常: orderNo={}", message.getOrderNo(), e);
                throw e;
            }
        };
    }
}
