package com.voltstack.ecommerce.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import com.voltstack.ecommerce.notification.service.DeadLetterWriter;
import com.voltstack.ecommerce.notification.service.EmailRenderer;
import com.voltstack.ecommerce.notification.service.EmailSender;
import com.voltstack.ecommerce.notification.service.NotificationQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventConsumerTest {

    private NotificationLogRepository logRepository;
    private NotificationQueue queue;
    private DeadLetterWriter deadLetterWriter;
    private EmailSender emailSender;
    private Acknowledgment ack;
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        logRepository = mock(NotificationLogRepository.class);
        queue = mock(NotificationQueue.class);
        deadLetterWriter = mock(DeadLetterWriter.class);
        emailSender = mock(EmailSender.class);
        ack = mock(Acknowledgment.class);
        consumer = new NotificationEventConsumer(new ObjectMapper(), logRepository, queue, deadLetterWriter,
                new EmailRenderer(), emailSender);
        when(logRepository.insert(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void envelopePayload_enqueuesAndAcks() {
        consumer.onEvent("""
                {"eventId":"%s","eventType":"OrderCreatedEvent","version":1,
                 "data":{"email":"buyer@example.com","orderId":"o1"}}
                """.formatted(UUID.randomUUID()), ack);
        verify(logRepository).insert(any(NotificationLog.class));
        verify(queue).enqueue(any(UUID.class), any(Instant.class));
        verify(ack).acknowledge();
    }

    @Test
    void flatOrderPayload_readsRootEmail() {
        consumer.onEvent("""
                {"eventId":"%s","eventType":"OrderCreatedEvent","orderId":"o1",
                 "orderNumber":"OR-1","email":"buyer@example.com","userId":"%s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()), ack);
        verify(logRepository).insert(any(NotificationLog.class));
        verify(queue).enqueue(any(UUID.class), any(Instant.class));
        verify(ack).acknowledge();
    }

    @Test
    void unknownEventType_acksWithoutSideEffects() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"SomethingElse\"}", ack);
        verify(logRepository, never()).insert(any(NotificationLog.class));
        verify(queue, never()).enqueue(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void duplicateEventId_acksAndSkips() {
        when(logRepository.insert(any(NotificationLog.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"OrderCreatedEvent\",\"email\":\"a@b.c\"}", ack);
        verify(queue, never()).enqueue(any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void missingRecipient_routesToDlqAndAcks() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"OrderCreatedEvent\"}", ack);
        verify(deadLetterWriter).record(anyString(), any(), anyString(), any(Boolean.class));
        verify(logRepository, never()).insert(any(NotificationLog.class));
        verify(ack).acknowledge();
    }

    @Test
    void malformedJson_routesToDlqAndAcks() {
        consumer.onEvent("not-json", ack);
        verify(deadLetterWriter).record(anyString(), any(), anyString(), any(Boolean.class));
        verify(ack).acknowledge();
    }

    @Test
    void enqueueFailure_removesDedupRowAndPropagates() {
        UUID eventId = UUID.randomUUID();
        doThrow(new RuntimeException("redis down")).when(queue).enqueue(any(UUID.class), any(Instant.class));
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> consumer.onEvent(
                        "{\"eventId\":\"" + eventId + "\",\"eventType\":\"OrderCreatedEvent\",\"email\":\"a@b.c\"}", ack));
        verify(logRepository).deleteByEventId(eventId);
        verify(ack, never()).acknowledge();
    }

    @Test
    void dedupLogCarriesRecipientTemplateAndStatus() {
        UUID eventId = UUID.randomUUID();
        consumer.onEvent("{\"eventId\":\"" + eventId + "\",\"eventType\":\"OrderCancelledEvent\",\"email\":\"a@b.c\",\"orderNumber\":\"OR-9\"}", ack);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(captor.capture());
        NotificationLog log = captor.getValue();
        assertEquals(eventId, log.getEventId());
        assertEquals("a@b.c", log.getRecipient());
        assertEquals("order-cancelled", log.getTemplate());
        assertEquals("PENDING", log.getStatus());
        assertEquals(0, log.getAttempts());
    }

    @Test
    void tokenKey_anyCase_strippedFromPersistedPayload() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"OrderCreatedEvent\","
                + "\"email\":\"a@b.c\",\"resetToken\":\"raw\",\"orderNumber\":\"OR-1\"}", ack);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(captor.capture());
        NotificationLog log = captor.getValue();
        assertFalse(log.getPayload().containsKey("resetToken"));
        assertFalse(log.getPayload().containsKey("reset_token"));
        assertEquals("OR-1", log.getPayload().get("orderNumber"));
    }
}
