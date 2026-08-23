package com.voltstack.ecommerce.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.notification.domain.DeadLetter;
import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.repository.DeadLetterRepository;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * NOT-007: every terminal failure is persisted to MongoDB {@code notification_dead_letters}
 * unconditionally; publishing the metadata envelope to Kafka {@code notification.dlq} is opt-in.
 */
@Slf4j
@Component
public class DeadLetterWriter {

    /** One-time verification/reset links must not be persisted or replayed (SRS 07 §4). */
    private static final Pattern SENSITIVE_LINK = Pattern.compile(
            "(\"(?:verifyLink|resetLink|verify_link|reset_link)\"\\s*:\\s*\")[^\"]*(\")");

    private final DeadLetterRepository deadLetterRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.kafka.dlq-topic:notification.dlq}")
    private String dlqTopic;

    @Value("${notification.kafka.dlq-publish-enabled:false}")
    private boolean dlqPublishEnabled;

    public DeadLetterWriter(DeadLetterRepository deadLetterRepository,
                            NotificationLogRepository notificationLogRepository,
                            KafkaTemplate<String, String> kafkaTemplate,
                            ObjectMapper objectMapper) {
        this.deadLetterRepository = deadLetterRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /** Consumer-side permanent failure (malformed event / missing recipient) before a job existed. */
    public void record(String rawMessage, UUID eventId, String reason, boolean permanent) {
        DeadLetter dl = DeadLetter.builder()
                .eventId(eventId)
                .reason(reason)
                .permanent(permanent)
                .payload(Map.of("rawMessage", SENSITIVE_LINK.matcher(rawMessage).replaceAll("$1[REDACTED]$2")))
                .build();
        saveAndPublish(dl);
        log.warn("Notification dead letter recorded eventId={} reason={}", eventId, reason);
    }

    /** Worker-side terminal failure after retries, or a permanent render error. */
    public void record(NotificationLog job, String reason, boolean permanent) {
        DeadLetter dl = DeadLetter.builder()
                .eventId(job.getEventId())
                .eventType(job.getEventType())
                .userId(job.getUserId())
                .recipient(job.getRecipient())
                .template(job.getTemplate())
                .payload(job.getPayload())
                .reason(reason)
                .permanent(permanent)
                .build();
        saveAndPublish(dl);
        job.setStatus("IN_DLQ");
        job.setError(reason);
        job.setLeaseUntil(null);
        job.setNextRetryAt(null);
        notificationLogRepository.save(job);
        log.warn("Notification {} eventId={} recipient={} routed to DLQ: {}", job.getTemplate(), job.getEventId(), job.getRecipient(), reason);
    }

    private void saveAndPublish(DeadLetter dl) {
        deadLetterRepository.save(dl);
        if (dlqPublishEnabled) {
            try {
                kafkaTemplate.send(dlqTopic, dl.getEventId() == null ? null : dl.getEventId().toString(),
                        objectMapper.writeValueAsString(dl));
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize DLQ event for eventId={}", dl.getEventId(), e);
            }
        }
    }
}
