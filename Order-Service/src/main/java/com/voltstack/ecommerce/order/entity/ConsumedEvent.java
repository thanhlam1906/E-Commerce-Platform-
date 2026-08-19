package com.voltstack.ecommerce.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "consumed_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "consumed_at", nullable = false, updatable = false)
    private Instant consumedAt;

    @PrePersist
    void onCreate() {
        consumedAt = Instant.now();
    }
}
