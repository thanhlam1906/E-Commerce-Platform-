package com.voltstack.ecommerce.order.repository;

import com.voltstack.ecommerce.order.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {

    Optional<ConsumedEvent> findByEventId(UUID eventId);
}
