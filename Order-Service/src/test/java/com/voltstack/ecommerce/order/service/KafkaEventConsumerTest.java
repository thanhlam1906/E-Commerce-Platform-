package com.voltstack.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderItem;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.repository.ConsumedEventRepository;
import com.voltstack.ecommerce.order.repository.OrderItemRepository;
import com.voltstack.ecommerce.order.repository.OrderRepository;
import com.voltstack.ecommerce.order.repository.OrderStatusHistoryRepository;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventConsumerTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderStatusHistoryRepository historyRepository;
    private ConsumedEventRepository consumedEventRepository;
    private OutboxRepository outboxRepository;
    private InventoryService inventoryService;
    private KafkaEventConsumer consumer;
    private UUID eventId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        historyRepository = mock(OrderStatusHistoryRepository.class);
        consumedEventRepository = mock(ConsumedEventRepository.class);
        outboxRepository = mock(OutboxRepository.class);
        inventoryService = mock(InventoryService.class);
        consumer = new KafkaEventConsumer(new ObjectMapper(), orderRepository, orderItemRepository,
                historyRepository, consumedEventRepository, inventoryService, outboxRepository);
        eventId = UUID.randomUUID();
        orderId = UUID.randomUUID();
    }

    private String paymentEvent(String eventType) {
        return "{\"eventId\":\"" + eventId + "\",\"orderId\":\"" + orderId + "\",\"eventType\":\"" + eventType + "\"}";
    }

    @Test
    void completedEvent_transitionsToConfirmed_noStockRelease() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.transition(orderId, "PENDING", "CONFIRMED")).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).email("buyer@example.com").userId(UUID.randomUUID()).build()));

        consumer.onPaymentEvent(paymentEvent("COMPLETED"));

        verify(orderRepository).transition(orderId, "PENDING", "CONFIRMED");
        verify(inventoryService, never()).release(anyString(), anyInt(), anyString());
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.CONFIRMED));
        verify(consumedEventRepository).save(argThat(c -> c.getEventId().equals(eventId) && "COMPLETED".equals(c.getEventType())));
        verify(outboxRepository).save(argThat(e -> "OrderStatusChangedEvent".equals(e.getEventType())
                && e.getPayload().contains("\"email\":\"buyer@example.com\"")
                && e.getPayload().contains("\"newStatus\":\"CONFIRMED\"")));
    }

    @Test
    void failedEvent_transitionsToCancelled_releasesStock() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.transition(orderId, "PENDING", "CANCELLED")).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).email("buyer@example.com").userId(UUID.randomUUID()).build()));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(OrderItem.builder().sku("SKU1").quantity(2).build()));

        consumer.onPaymentEvent(paymentEvent("FAILED"));

        verify(orderRepository).transition(orderId, "PENDING", "CANCELLED");
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.CANCELLED));
        verify(consumedEventRepository).save(argThat(c -> c.getEventId().equals(eventId) && "FAILED".equals(c.getEventType())));
        verify(outboxRepository).save(argThat(e -> "OrderStatusChangedEvent".equals(e.getEventType())
                && e.getPayload().contains("\"email\":\"buyer@example.com\"")
                && e.getPayload().contains("\"newStatus\":\"CANCELLED\"")));
    }

    @Test
    void timeoutEvent_transitionsToExpired_releasesStock() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.transition(orderId, "PENDING", "EXPIRED")).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).email("buyer@example.com").userId(UUID.randomUUID()).build()));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(OrderItem.builder().sku("SKU1").quantity(2).build()));

        consumer.onPaymentEvent(paymentEvent("TIMEOUT"));

        verify(orderRepository).transition(orderId, "PENDING", "EXPIRED");
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.EXPIRED));
        verify(consumedEventRepository).save(argThat(c -> c.getEventId().equals(eventId) && "TIMEOUT".equals(c.getEventType())));
        verify(outboxRepository).save(argThat(e -> "OrderStatusChangedEvent".equals(e.getEventType())
                && e.getPayload().contains("\"email\":\"buyer@example.com\"")
                && e.getPayload().contains("\"newStatus\":\"EXPIRED\"")));
    }

    @Test
    void refundedEvent_isNoOpButConsumed() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onPaymentEvent(paymentEvent("REFUNDED"));

        verify(orderRepository, never()).transition(any(), any(), any());
        verify(inventoryService, never()).release(anyString(), anyInt(), anyString());
        verify(historyRepository, never()).save(any());
        verify(consumedEventRepository).save(argThat(c -> c.getEventId().equals(eventId) && "REFUNDED".equals(c.getEventType())));
    }

    @Test
    void malformedJson_skippedWithoutException() {
        consumer.onPaymentEvent("not json at all");

        verify(consumedEventRepository, never()).save(any());
        verify(orderRepository, never()).transition(any(), any(), any());
    }

    @Test
    void unknownEventType_skippedWithoutException() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);

        consumer.onPaymentEvent(paymentEvent("Bogus"));

        verify(orderRepository, never()).transition(any(), any(), any());
        verify(consumedEventRepository, never()).save(any());
    }

    @Test
    void duplicateEventId_noOp() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(true);

        consumer.onPaymentEvent(paymentEvent("COMPLETED"));

        verify(orderRepository, never()).transition(any(), any(), any());
        verify(consumedEventRepository, never()).save(any());
    }

    /** The documented payment-event forms ("PaymentCompleted", "payment_completed",
     *  "PaymentCompletedEvent") must all reach the COMPLETED branch. */
    @Test
    void paymentCompletedEvent_normalizesToCompleted() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.transition(orderId, "PENDING", "CONFIRMED")).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).email("buyer@example.com").userId(UUID.randomUUID()).build()));

        consumer.onPaymentEvent(paymentEvent("PaymentCompletedEvent"));

        verify(orderRepository).transition(orderId, "PENDING", "CONFIRMED");
        verify(consumedEventRepository).save(argThat(c -> c.getEventType().equals("COMPLETED")));
    }

    @Test
    void paymentCompleted_snakeCase_normalizesToCompleted() {
        when(consumedEventRepository.existsById(eventId)).thenReturn(false);
        when(orderRepository.transition(orderId, "PENDING", "CONFIRMED")).thenReturn(1);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(
                Order.builder().id(orderId).email("buyer@example.com").userId(UUID.randomUUID()).build()));

        consumer.onPaymentEvent(paymentEvent("payment_completed"));

        verify(orderRepository).transition(orderId, "PENDING", "CONFIRMED");
    }
}
