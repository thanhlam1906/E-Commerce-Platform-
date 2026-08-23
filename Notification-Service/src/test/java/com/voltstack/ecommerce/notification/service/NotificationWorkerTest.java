package com.voltstack.ecommerce.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.notification.domain.DeadLetter;
import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.exception.PermanentFailureException;
import com.voltstack.ecommerce.notification.repository.DeadLetterRepository;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationWorkerTest {

    private NotificationQueue queue;
    private NotificationLogRepository logRepository;
    private DeadLetterRepository deadLetterRepository;
    private EmailRenderer renderer;
    private EmailSender emailSender;
    private RateLimiter rateLimiter;
    private NotificationWorker worker;

    @BeforeEach
    void setUp() {
        queue = mock(NotificationQueue.class);
        logRepository = mock(NotificationLogRepository.class);
        deadLetterRepository = mock(DeadLetterRepository.class);
        renderer = mock(EmailRenderer.class);
        emailSender = mock(EmailSender.class);
        rateLimiter = mock(RateLimiter.class);
        // Real writer so the worker's DLQ path also mutates job.status -> IN_DLQ.
        DeadLetterWriter deadLetterWriter = new DeadLetterWriter(deadLetterRepository, logRepository,
                mock(KafkaTemplate.class), new ObjectMapper());
        worker = new NotificationWorker(queue, logRepository, renderer, emailSender, deadLetterWriter, rateLimiter);
        ReflectionTestUtils.setField(worker, "maxAttempts", 3);
        ReflectionTestUtils.setField(worker, "firstDelaySeconds", 30L);
        ReflectionTestUtils.setField(worker, "secondDelaySeconds", 60L);
        ReflectionTestUtils.setField(worker, "leaseSeconds", 60);
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        when(rateLimiter.isLimited(any())).thenReturn(false);
        when(renderer.render(any(), any())).thenReturn(new EmailRenderer.RenderedEmail("s", "html", "text"));
    }

    private NotificationLog job(int attempts, String status) {
        return NotificationLog.builder()
                .eventId(UUID.randomUUID()).eventType("OrderCreatedEvent").recipient("a@b.c")
                .template("order-confirmation").payload(Map.of("orderNumber", "OR-1"))
                .status(status).attempts(attempts).build();
    }

    @Test
    void transientFailure_firstAttempt_reschedules30s() {
        NotificationLog job = job(0, "PENDING");
        when(queue.claimDue(any(int.class), any(int.class))).thenReturn(List.of(job));
        when(emailSender.send(any(), any(), any(), any())).thenThrow(new RuntimeException("smtp down"));

        worker.poll();

        assertEquals(1, job.getAttempts());
        assertTrue(job.getNextRetryAt().isAfter(Instant.now().plusSeconds(25)));
        assertTrue(job.getNextRetryAt().isBefore(Instant.now().plusSeconds(35)));
        verify(logRepository).save(job);
        verify(queue).reschedule(job.getEventId(), job.getNextRetryAt());
        verify(deadLetterRepository, never()).save(any(DeadLetter.class));
    }

    @Test
    void transientFailure_maxAttempts_routesToDlq() {
        NotificationLog job = job(2, "PENDING");
        when(queue.claimDue(any(int.class), any(int.class))).thenReturn(List.of(job));
        when(emailSender.send(any(), any(), any(), any())).thenThrow(new RuntimeException("smtp down"));

        worker.poll();

        assertEquals("IN_DLQ", job.getStatus());
        verify(deadLetterRepository).save(any(DeadLetter.class));
        verify(queue).remove(job.getEventId());
        verify(queue, never()).reschedule(any(), any());
    }

    @Test
    void permanentRenderError_routesToDlqImmediately() {
        NotificationLog job = job(0, "PENDING");
        when(queue.claimDue(any(int.class), any(int.class))).thenReturn(List.of(job));
        when(renderer.render(any(), any())).thenThrow(new PermanentFailureException("missing variable"));

        worker.poll();

        assertEquals("IN_DLQ", job.getStatus());
        verify(deadLetterRepository).save(any(DeadLetter.class));
        verify(emailSender, never()).send(any(), any(), any(), any());
        verify(queue).remove(job.getEventId());
    }

    @Test
    void success_marksSentAndRemovesFromQueue() {
        NotificationLog job = job(0, "PENDING");
        when(queue.claimDue(any(int.class), any(int.class))).thenReturn(List.of(job));
        when(emailSender.send(any(), any(), any(), any())).thenReturn("provider-id");

        worker.poll();

        assertEquals("SENT", job.getStatus());
        assertEquals(1, job.getAttempts());
        assertEquals("provider-id", job.getProviderMsgId());
        verify(logRepository).save(job);
        verify(queue).remove(job.getEventId());
        verify(deadLetterRepository, never()).save(any(DeadLetter.class));
    }

    @Test
    void rateLimited_delaysJobWithoutConsumingAttempt() {
        NotificationLog job = job(0, "PENDING");
        when(queue.claimDue(any(int.class), any(int.class))).thenReturn(List.of(job));
        when(rateLimiter.isLimited(any())).thenReturn(true);
        when(rateLimiter.retryDelaySeconds()).thenReturn(60L);

        worker.poll();

        assertEquals(0, job.getAttempts());
        assertTrue(job.getNextRetryAt().isAfter(Instant.now().plusSeconds(55)));
        verify(queue).reschedule(job.getEventId(), job.getNextRetryAt());
        verify(emailSender, never()).send(any(), any(), any(), any());
    }
}
