package com.voltstack.ecommerce.order.service;

import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PlatformTransactionManager transactionManager;

    @Value("${order.kafka-topic}")
    private String topic;

    @Scheduled(fixedDelayString = "${order.outbox-poll-delay-ms:1000}")
    public void publishPending() {
        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAt();
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(topic, event.getPayload()).get(5, TimeUnit.SECONDS);
                // One short transaction per event so a slow/broken broker never holds a single
                // DB connection for the whole batch. A send-then-rollback resends (at-least-once);
                // consumers dedup.
                new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                    event.setPublished(true);
                    outboxRepository.save(event);
                });
            } catch (Exception e) {
                log.warn("Publish outbox event {} failed, will retry on next poll", event.getId(), e);
            }
        }
    }
}
