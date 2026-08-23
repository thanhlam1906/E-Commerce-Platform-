package com.voltstack.ecommerce.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.payment.entity.OutboxEvent;
import com.voltstack.ecommerce.payment.entity.Transaction;
import com.voltstack.ecommerce.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes payment result events to the outbox (inside the caller's transaction) instead of firing
 * Kafka directly. A scheduled {@link OutboxPublisher} relays them to payment.events at-least-once.
 * eventType values (COMPLETED/FAILED/TIMEOUT/REFUNDED) match Order-Service's KafkaEventConsumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void publishCompleted(Transaction txn) {
        publish("COMPLETED", txn, Map.of(
                "eventType", "COMPLETED",
                "orderId", txn.getOrderId().toString(),
                "transactionId", txn.getId().toString(),
                "amount", txn.getAmount(),
                "status", "SUCCESS"));
    }

    public void publishFailed(Transaction txn, String reason) {
        publish("FAILED", txn, Map.of(
                "eventType", "FAILED",
                "orderId", txn.getOrderId().toString(),
                "transactionId", txn.getId().toString(),
                "reason", reason));
    }

    public void publishTimeout(Transaction txn) {
        publish("TIMEOUT", txn, Map.of(
                "eventType", "TIMEOUT",
                "orderId", txn.getOrderId().toString(),
                "transactionId", txn.getId().toString()));
    }

    public void publishRefunded(Transaction txn) {
        publish("REFUNDED", txn, Map.of(
                "eventType", "REFUNDED",
                "orderId", txn.getOrderId().toString(),
                "transactionId", txn.getId().toString(),
                "refundAmount", txn.getRefundAmount()));
    }

    private void publish(String eventType, Transaction txn, Map<String, Object> body) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventId", UUID.randomUUID().toString());
            envelope.putAll(body);
            outboxRepository.save(OutboxEvent.builder()
                    .eventType(eventType)
                    .aggregateId(txn.getId().toString())
                    .payload(objectMapper.writeValueAsString(envelope))
                    .published(false)
                    .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize payment event", e);
        }
    }
}
