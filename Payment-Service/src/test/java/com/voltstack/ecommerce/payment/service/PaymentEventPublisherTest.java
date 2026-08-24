package com.voltstack.ecommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.payment.entity.Gateway;
import com.voltstack.ecommerce.payment.entity.OutboxEvent;
import com.voltstack.ecommerce.payment.entity.Transaction;
import com.voltstack.ecommerce.payment.entity.TransactionStatus;
import com.voltstack.ecommerce.payment.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentEventPublisherTest {

    private OutboxRepository outboxRepository;
    private PaymentEventPublisher publisher;
    private Transaction txn;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        publisher = new PaymentEventPublisher(outboxRepository, new ObjectMapper());
        txn = Transaction.builder()
                .id(UUID.randomUUID()).orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("VND").paymentMethod("CARD")
                .gateway(Gateway.VNPAY).status(TransactionStatus.PENDING)
                .refundAmount(new BigDecimal("100.00"))
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test
    void publishCompleted_writesOutboxRowNotKafka() {
        publisher.publishCompleted(txn);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("COMPLETED", event.getEventType());
        assertEquals(txn.getOrderId().toString(), event.getAggregateId());
        assertFalse(event.getPublished());
        assertNotNull(event.getPayload());
        assertTrue(event.getPayload().contains("\"eventType\":\"COMPLETED\""));
        assertTrue(event.getPayload().contains("\"orderId\":\"" + txn.getOrderId() + "\""));
        assertTrue(event.getPayload().contains("\"transactionId\":\"" + txn.getId() + "\""));
        assertTrue(event.getPayload().contains("\"status\":\"SUCCESS\""));
        assertTrue(event.getPayload().contains("\"eventId\":\""));
    }

    @Test
    void publishRefunded_includesRefundAmount() {
        publisher.publishRefunded(txn);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent event = captor.getValue();
        assertEquals("REFUNDED", event.getEventType());
        assertTrue(event.getPayload().contains("\"refundAmount\":100.00"));
    }
}
