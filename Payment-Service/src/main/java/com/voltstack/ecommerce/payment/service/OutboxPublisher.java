package com.voltstack.ecommerce.payment.service;

import com.voltstack.ecommerce.payment.entity.OutboxEvent;
import com.voltstack.ecommerce.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Relays unpublished outbox rows to payment.events at-least-once. A row is marked published only
 * after a successful (synchronous, 5s-bounded) Kafka send; failures retry on the next poll.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${payment.kafka-topic}")
    private String topic;

    @Scheduled(fixedDelayString = "${payment.outbox-poll-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> events = outboxRepository.findTop100ByPublishedFalseOrderByCreatedAt();
        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(topic, event.getPayload()).get(5, TimeUnit.SECONDS);
                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception e) {
                log.warn("Publish outbox event {} failed, will retry on next poll", event.getId(), e);
            }
        }
    }
}
