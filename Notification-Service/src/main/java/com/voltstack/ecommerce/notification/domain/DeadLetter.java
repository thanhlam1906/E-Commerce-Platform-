package com.voltstack.ecommerce.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Dead letter record. Every terminal failure lands here; Kafka publishing to
 * {@code notification.dlq} is opt-in via {@code notification.kafka.dlq-publish-enabled}.
 */
@Document("notification_dead_letters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetter {

    @Id
    private String id;

    @Field("event_id")
    private UUID eventId;

    @Field("event_type")
    private String eventType;

    @Field("user_id")
    private UUID userId;

    private String recipient;

    private String template;

    private Map<String, Object> payload;

    private String reason;

    /** true = permanent failure (no retry would help); false = retries exhausted. */
    private Boolean permanent;

    @Field("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();
}
