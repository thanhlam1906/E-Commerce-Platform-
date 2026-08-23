package com.voltstack.ecommerce.notification.repository;

import com.voltstack.ecommerce.notification.domain.NotificationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {

    Optional<NotificationLog> findByEventId(UUID eventId);

    void deleteByEventId(UUID eventId);
}
