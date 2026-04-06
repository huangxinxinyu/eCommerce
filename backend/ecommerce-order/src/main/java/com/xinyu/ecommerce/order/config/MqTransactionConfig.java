package com.xinyu.ecommerce.order.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Configuration
public class MqTransactionConfig {

    private final ConcurrentHashMap<String, LocalTransactionState> transactionStates = new ConcurrentHashMap<>();

    @Bean
    @Primary
    public TransactionMQProducer transactionMQProducer() {
        TransactionMQProducer producer = new TransactionMQProducer("order-transaction-producer");
        producer.setNamesrvAddr("localhost:9876");
        producer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
                String transactionId = msg.getTransactionId();
                String key = msg.getKeys();
                log.info("执行本地事务: transactionId={}, topic={}, key={}", transactionId, msg.getTopic(), key);

                try {
                    transactionStates.put(key, LocalTransactionState.UNKNOW);

                    if (arg instanceof Runnable) {
                        ((Runnable) arg).run();
                    }

                    transactionStates.put(key, LocalTransactionState.COMMIT_MESSAGE);
                    log.info("本地事务执行成功，提交消息: key={}", key);
                    return LocalTransactionState.COMMIT_MESSAGE;

                } catch (Exception e) {
                    log.error("本地事务执行失败，回滚消息: key={}", key, e);
                    transactionStates.put(key, LocalTransactionState.ROLLBACK_MESSAGE);
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                String key = msg.getKeys();
                LocalTransactionState state = transactionStates.getOrDefault(key, LocalTransactionState.UNKNOW);
                log.info("检查本地事务状态: key={}, state={}", key, state);
                return state;
            }
        });
        return producer;
    }
}
