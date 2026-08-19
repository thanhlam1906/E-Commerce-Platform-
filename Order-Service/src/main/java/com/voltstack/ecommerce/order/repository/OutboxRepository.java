package com.voltstack.ecommerce.order.repository;

import com.voltstack.ecommerce.order.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop100ByPublishedFalseOrderByCreatedAt();

    boolean existsByEventTypeAndAggregateIdAndPublishedFalse(String eventType, String aggregateId);

    java.util.Optional<OutboxEvent> findByEventTypeAndAggregateId(String eventType, String aggregateId);

    void deleteByEventTypeAndAggregateId(String eventType, String aggregateId);
}
