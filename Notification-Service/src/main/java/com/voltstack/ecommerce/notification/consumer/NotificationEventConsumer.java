package com.voltstack.ecommerce.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.exception.MalformedEventException;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import com.voltstack.ecommerce.notification.service.DeadLetterWriter;
import com.voltstack.ecommerce.notification.service.NotificationQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes the shared event envelope (docs/requirements/08 §3-4) from a list of topics.
 * Parses both the versioned envelope ({@code data:{...}}) and the flat Order payload.
 * Dedup is the unique {@code notification_logs.event_id} insert: a duplicate is acked and skipped.
 * Unknown event types are acked without side effects.
 */
@Slf4j
@Component
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationQueue queue;
    private final DeadLetterWriter deadLetterWriter;

    public NotificationEventConsumer(ObjectMapper objectMapper,
                                     NotificationLogRepository notificationLogRepository,
                                     NotificationQueue queue,
                                     DeadLetterWriter deadLetterWriter) {
        this.objectMapper = objectMapper;
        this.notificationLogRepository = notificationLogRepository;
        this.queue = queue;
        this.deadLetterWriter = deadLetterWriter;
    }

    @KafkaListener(topics = "#{'${notification.kafka.events-topic}'.split(',')}", groupId = "notification-service")
    public void onEvent(String message, Acknowledgment ack) {
        try {
            JsonNode node = objectMapper.readTree(message);
            UUID eventId = parseUuid(text(node, "eventId", "event_id"));
            String eventType = text(node, "eventType", "event_type");
            if (eventId == null || eventType.isBlank()) {
                throw new MalformedEventException("missing eventId/eventType", eventId);
            }
            String template = templateFor(eventType);
            if (template == null) {
                log.info("Unknown notification event type '{}', ack without side effect", eventType);
                ack.acknowledge();
                return;
            }
            JsonNode data = node.has("data") ? node.get("data") : node;
            String email = findEmail(node, data);
            if (email.isBlank()) {
                throw new MalformedEventException("recipient email missing for " + eventType, eventId);
            }
            Map<String, Object> payload = objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {});
            UUID userId = parseUuid(text(data, "userId", "user_id"));

            NotificationLog entry = NotificationLog.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .userId(userId)
                    .recipient(email.trim())
                    .channel("EMAIL")
                    .template(template)
                    .payload(payload)
                    .status("PENDING")
                    .attempts(0)
                    .build();
            try {
                notificationLogRepository.insert(entry);
            } catch (DuplicateKeyException e) {
                log.info("Duplicate event {} already processed, ack and skip", eventId);
                ack.acknowledge();
                return;
            }
            try {
                queue.enqueue(eventId, Instant.now());
            } catch (RuntimeException e) {
                // No durable job was scheduled; remove the dedup row so redelivery can retry cleanly.
                notificationLogRepository.deleteByEventId(eventId);
                throw e;
            }
            ack.acknowledge();
        } catch (MalformedEventException | JsonProcessingException | IllegalArgumentException e) {
            // Never log the raw message body: verification/reset events carry a one-time
            // token in verifyLink/resetLink, and those must not appear in logs (SRS 07 §4).
            log.warn("Malformed notification event, routing to DLQ: {}", e.getMessage(), e);
            UUID eventId = e instanceof MalformedEventException m ? m.getEventId() : null;
            deadLetterWriter.record(message, eventId, "malformed: " + e.getMessage(), true);
            ack.acknowledge();
        }
        // Any other RuntimeException (queue/Mongo unavailable) propagates without ack → Kafka redelivers.
    }

    private String templateFor(String eventType) {
        String n = eventType.replace("Event", "").replace("_", "").toUpperCase();
        return switch (n) {
            case "ORDERCREATED" -> "order-confirmation";
            case "ORDERSTATUSCHANGED" -> "order-status-updated";
            case "ORDERCANCELLED" -> "order-cancelled";
            case "PAYMENTCOMPLETED" -> "payment-confirmed";
            case "USERREGISTERED" -> "email-verification";
            case "PASSWORDRESETREQUESTED" -> "password-reset";
            default -> null;
        };
    }

    private String findEmail(JsonNode node, JsonNode data) {
        String[] keys = {"email", "recipientEmail", "userEmail", "customerEmail"};
        for (String key : keys) {
            String v = text(data, key);
            if (!v.isBlank()) {
                return v;
            }
        }
        for (String key : keys) {
            String v = text(node, key);
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            // isNull(): a JSON null must be treated as missing, never the literal string "null".
            if (node != null && node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return "";
    }

    private UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
