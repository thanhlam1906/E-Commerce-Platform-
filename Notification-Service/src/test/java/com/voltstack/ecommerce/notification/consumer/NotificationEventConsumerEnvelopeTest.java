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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventConsumerEnvelopeTest {

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
        when(emailSender.send(anyString(), anyString(), anyString(), any())).thenReturn("provider-id");
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
    void userRegisteredEvent_rendersWithLinkButPersistsScrubbedPayload() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"UserRegisteredEvent\","
                + "\"data\":{\"email\":\"u@a.b\",\"verifyLink\":\"https://id.local/verify?t=secret\"}}", ack);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(captor.capture());
        NotificationLog log = captor.getValue();
        assertEquals("email-verification", log.getTemplate());
        assertEquals("SENT", log.getStatus());
        // Token-bearing link is stripped from the persisted log payload...
        assertFalse(log.getPayload().containsKey("verifyLink"));
        assertFalse(log.getPayload().containsKey("verify_link"));
        assertFalse(log.getPayload().toString().contains("secret"));
        // ...while the email was still rendered and sent with the full link.
        ArgumentCaptor<EmailRenderer.RenderedEmail> emailCaptor =
                ArgumentCaptor.forClass(EmailRenderer.RenderedEmail.class);
        verify(emailSender).send(eq("u@a.b"), anyString(), eq("email-verification"), emailCaptor.capture());
        assertTrue(emailCaptor.getValue().html().contains("https://id.local/verify?t=secret"));
        assertTrue(emailCaptor.getValue().text().contains("https://id.local/verify?t=secret"));
        verify(queue, never()).enqueue(any(), any());
    }

    @Test
    void passwordResetEvent_rendersWithLinkButPersistsScrubbedPayload() {
        consumer.onEvent("{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"PasswordResetRequestedEvent\","
                + "\"data\":{\"email\":\"u@a.b\",\"resetLink\":\"https://id.local/reset?t=secret\"}}", ack);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).insert(captor.capture());
        NotificationLog log = captor.getValue();
        assertEquals("password-reset", log.getTemplate());
        assertEquals("SENT", log.getStatus());
        // Token-bearing link is stripped from the persisted log payload...
        assertFalse(log.getPayload().containsKey("resetLink"));
        assertFalse(log.getPayload().containsKey("reset_link"));
        assertFalse(log.getPayload().toString().contains("secret"));
        // ...while the email was still rendered and sent with the full link.
        ArgumentCaptor<EmailRenderer.RenderedEmail> emailCaptor =
                ArgumentCaptor.forClass(EmailRenderer.RenderedEmail.class);
        verify(emailSender).send(eq("u@a.b"), anyString(), eq("password-reset"), emailCaptor.capture());
        assertTrue(emailCaptor.getValue().html().contains("https://id.local/reset?t=secret"));
        assertTrue(emailCaptor.getValue().text().contains("https://id.local/reset?t=secret"));
        verify(queue, never()).enqueue(any(), any());
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
