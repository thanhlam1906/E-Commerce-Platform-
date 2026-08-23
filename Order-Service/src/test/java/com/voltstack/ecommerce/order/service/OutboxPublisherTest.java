package com.voltstack.ecommerce.order.service;

import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    private OutboxRepository outboxRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        publisher = new OutboxPublisher(outboxRepository, kafkaTemplate, txManager);
        ReflectionTestUtils.setField(publisher, "topic", "orders-topic");
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> okFuture() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    @Test
    void publishPending_sendsAndMarksPublishedOnSuccess() {
        OutboxEvent e1 = OutboxEvent.builder().id(UUID.randomUUID()).payload("{\"a\":1}").published(false).build();
        OutboxEvent e2 = OutboxEvent.builder().id(UUID.randomUUID()).payload("{\"b\":2}").published(false).build();
        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAt()).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send("orders-topic", "{\"a\":1}")).thenReturn(okFuture());
        when(kafkaTemplate.send("orders-topic", "{\"b\":2}")).thenReturn(okFuture());

        publisher.publishPending();

        assertTrue(e1.getPublished());
        assertTrue(e2.getPublished());
        verify(kafkaTemplate, times(2)).send(anyString(), anyString());
        verify(outboxRepository, times(2)).save(any(OutboxEvent.class));
    }

    @Test
    void publishPending_sendFailure_keepsUnpublishedNoException() {
        OutboxEvent e1 = OutboxEvent.builder().id(UUID.randomUUID()).payload("x").published(false).build();
        when(outboxRepository.findTop100ByPublishedFalseOrderByCreatedAt()).thenReturn(List.of(e1));
        when(kafkaTemplate.send(anyString(), anyString())).thenThrow(new RuntimeException("kafka down"));

        publisher.publishPending(); // must not propagate

        assertFalse(e1.getPublished());
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }
}
