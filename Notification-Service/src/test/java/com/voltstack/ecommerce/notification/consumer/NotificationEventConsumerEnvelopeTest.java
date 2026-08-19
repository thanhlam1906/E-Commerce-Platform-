package com.voltstack.ecommerce.notification.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import com.voltstack.ecommerce.notification.service.DeadLetterWriter;
import com.voltstack.ecommerce.notification.service.NotificationQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventConsumerEnvelopeTest {

    private NotificationLogRepository logRepository;
    private NotificationQueue queue;
    private DeadLetterWriter deadLetterWriter;
    private Acknowledgment ack;
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        logRepository = mock(NotificationLogRepository.class);
        queue = mock(NotificationQueue.class);
        deadLetterWriter = mock(DeadLetterWriter.class);
        ack = mock(Acknowledgment.class);
        consumer = new NotificationEventConsumer(new ObjectMapper(), logRepository, queue, deadLetterWriter);
        when(logRepository.insert(any(NotificationLog.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private String envelopeWithDataKey(String eventType, String key, String value) {
        return """
                {"eventId":"%s","eventType":"%s","version":1,"data":{"%s":"%s","orderId":"o1"}}
                """.formatted(UUID.randomUUID(), eventType, key, value);
    }

    @Test
    void nestedRecipientUserCustomerEmails_allMapToRecipient() {
        for (String key : List.of("recipientEmail", "userEmail", "customerEmail")) {
            consumer.onEvent(envelopeWithDataKey("OrderCreatedEvent", key, "buyer@example.com"), ack);
        }
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(3)).insert(captor.capture());
        captor.getAllValues().forEach(log -> assertEquals("buyer@example.com", log.getRecipient()));
        verify(queue, times(3)).enqueue(any(UUID.class), any(Instant.class));
        verify(ack, times(3)).acknowledge();
    }

    @Test
    void snakeCaseEnvelopeKeys_parse() {
        consumer.onEvent("{\"event_id\":\"" + UUID.randomUUID()
                + "\",\"event_type\":\"OrderCreatedEvent\",\"data\":{\"email\":\"a@b.c\"}}", ack);
        verify(logRepository).insert(any(NotificationLog.class));
        verify(queue).enqueue(any(UUID.class), any(Instant.class));
        verify(ack).acknowledge();
    }

    @Test
    void userRegisteredEvent_mapsToEmailVerificationTemplateAndKeepsLink() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"UserRegisteredEvent\","
                + "\"data\":{\"email\":\"u@a.b\",\"verifyLink\":\"https://id.local/verify\"}}", ack);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(captor.capture());
        assertEquals("email-verification", captor.getValue().getTemplate());
        assertEquals("https://id.local/verify", captor.getValue().getPayload().get("verifyLink"));
    }

    @Test
    void passwordResetEvent_mapsToPasswordResetTemplateAndKeepsLink() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PasswordResetRequestedEvent\","
                + "\"data\":{\"email\":\"u@a.b\",\"resetLink\":\"https://id.local/reset\"}}", ack);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(captor.capture());
        assertEquals("password-reset", captor.getValue().getTemplate());
        assertEquals("https://id.local/reset", captor.getValue().getPayload().get("resetLink"));
    }

    @Test
    void duplicateEventId_acksAndSkipsWithoutRoutingToDlq() {
        when(logRepository.insert(any(NotificationLog.class)))
                .thenThrow(new DuplicateKeyException("dup"));
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"eventType\":\"OrderCreatedEvent\",\"email\":\"a@b.c\"}", ack);
        verify(queue, never()).enqueue(any(), any());
        verify(deadLetterWriter, never()).record(anyString(), any(), anyString(), anyBoolean());
        verify(ack).acknowledge();
    }
}
