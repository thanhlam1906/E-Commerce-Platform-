package com.voltstack.ecommerce.notification.repository;

import com.voltstack.ecommerce.notification.domain.DeadLetter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DeadLetterRepository extends MongoRepository<DeadLetter, String> {
}
