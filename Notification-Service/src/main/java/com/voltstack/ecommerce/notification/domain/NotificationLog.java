package com.voltstack.ecommerce.notification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Durable per-event record. The unique {@code event_id} index is the consumer-side
 * dedup gate, and the same document doubles as the job (status PENDING + retry state)
 * for the mongo queue worker. Field names match SRS 07 §6.
 */
@Document("notification_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        @CompoundIndex(def = "{'user_id': 1, 'created_at': -1}"),
        @CompoundIndex(def = "{'status': 1, 'created_at': 1}")
})
public class NotificationLog {

    @Id
    private String id;

    @Field("event_id")
    @Indexed(unique = true)
    private UUID eventId;

    @Field("event_type")
    private String eventType;

    @Field("user_id")
    private UUID userId;

    private String recipient;

    private String channel;

    private String template;

    private Map<String, Object> payload;

    /** PENDING (queued) | SENT | FAILED | IN_DLQ */
    private String status;

    private Integer attempts;

    private String error;

    @Field("created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Field("sent_at")
    private Instant sentAt;

    @Field("provider_msg_id")
    private String providerMsgId;

    @Field("next_retry_at")
    private Instant nextRetryAt;

    @Field("lease_until")
    private Instant leaseUntil;
}
