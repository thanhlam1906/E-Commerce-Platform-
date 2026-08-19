package com.voltstack.ecommerce.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.voltstack.ecommerce.notification.domain.DeadLetter;
import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.repository.DeadLetterRepository;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeadLetterWriterTest {

    private DeadLetterRepository deadLetterRepository;
    private NotificationLogRepository logRepository;
    private KafkaTemplate<String, String> kafka;
    private DeadLetterWriter writer;

    @BeforeEach
    void setUp() {
        deadLetterRepository = mock(DeadLetterRepository.class);
        logRepository = mock(NotificationLogRepository.class);
        kafka = mock(KafkaTemplate.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // DeadLetter carries Instant createdAt
        writer = new DeadLetterWriter(deadLetterRepository, logRepository, kafka, mapper);
        ReflectionTestUtils.setField(writer, "dlqTopic", "notification.dlq");
    }

    @Test
    void record_rawMessage_alwaysPersistsNoKafkaWhenDisabled() {
        writer.record("raw-json", UUID.randomUUID(), "recipient email missing", true);
        verify(deadLetterRepository).save(any(DeadLetter.class));
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void record_rawMessage_publishesToKafkaTopicWhenEnabled() {
        ReflectionTestUtils.setField(writer, "dlqPublishEnabled", true);
        UUID eventId = UUID.randomUUID();

        writer.record("raw-json", eventId, "recipient email missing", true);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(topic.capture(), key.capture(), anyString());
        assertEquals("notification.dlq", topic.getValue());
        assertEquals(eventId.toString(), key.getValue());
    }

    @Test
    void record_job_marksInDlqAndPersistsBothCollections() {
        NotificationLog job = NotificationLog.builder()
                .eventId(UUID.randomUUID()).eventType("OrderCreatedEvent").recipient("a@b.c")
                .template("order-confirmation").payload(Map.of("orderNumber", "OR-1"))
                .status("PENDING").attempts(3).build();

        writer.record(job, "smtp down", false);

        assertEquals("IN_DLQ", job.getStatus());
        assertEquals("smtp down", job.getError());
        assertNull(job.getLeaseUntil());
        assertNull(job.getNextRetryAt());
        verify(deadLetterRepository).save(any(DeadLetter.class));
        verify(logRepository).save(job);
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
