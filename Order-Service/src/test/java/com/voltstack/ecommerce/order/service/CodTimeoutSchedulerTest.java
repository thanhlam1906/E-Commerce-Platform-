package com.voltstack.ecommerce.order.service;

import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodTimeoutSchedulerTest {

    private OrderRepository orderRepository;
    private OrderService orderService;
    private CodTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderService = mock(OrderService.class);
        scheduler = new CodTimeoutScheduler(orderRepository, orderService);
        ReflectionTestUtils.setField(scheduler, "codPendingTimeoutMinutes", 30L);
    }

    private Order codPending(UUID id) {
        return Order.builder().id(id).status(OrderStatus.PENDING).paymentMethod("COD").paymentGateway("COD").build();
    }

    @Test
    void cancelTimedOutCodOrders_expiredCodPending_cancelled() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByStatusAndPaymentGatewayAndCreatedAtBefore(
                eq(OrderStatus.PENDING), eq("COD"), any(Instant.class)))
                .thenReturn(List.of(codPending(id)));
        when(orderService.expireCodPending(id)).thenReturn(1);

        scheduler.cancelTimedOutCodOrders();

        verify(orderService).expireCodPending(id);
    }

    @Test
    void cancelTimedOutCodOrders_queriesOnlyCodPending() {
        UUID cod = UUID.randomUUID();
        UUID online = UUID.randomUUID();
        Order onlinePending = Order.builder().id(online).status(OrderStatus.PENDING)
                .paymentMethod("VNPAY_QR").paymentGateway("VNPAY").build();
        when(orderRepository.findByStatusAndPaymentGatewayAndCreatedAtBefore(
                eq(OrderStatus.PENDING), eq("COD"), any(Instant.class)))
                .thenReturn(List.of(codPending(cod), onlinePending));

        scheduler.cancelTimedOutCodOrders();

        // Repository narrows to COD; expireCodPending stays as defense in depth.
        verify(orderService).expireCodPending(cod);
        verify(orderService).expireCodPending(online);
        verify(orderRepository).findByStatusAndPaymentGatewayAndCreatedAtBefore(
                eq(OrderStatus.PENDING), eq("COD"), any(Instant.class));
    }

    @Test
    void cancelTimedOutCodOrders_transitionLost_notCountedTwice() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByStatusAndPaymentGatewayAndCreatedAtBefore(
                eq(OrderStatus.PENDING), eq("COD"), any(Instant.class)))
                .thenReturn(List.of(codPending(id)));
        when(orderService.expireCodPending(id)).thenReturn(0); // already cancelled by admin

        scheduler.cancelTimedOutCodOrders();

        verify(orderService, times(1)).expireCodPending(id);
    }

    @Test
    void cancelTimedOutCodOrders_noPendingOrders_noCalls() {
        when(orderRepository.findByStatusAndPaymentGatewayAndCreatedAtBefore(
                eq(OrderStatus.PENDING), eq("COD"), any(Instant.class)))
                .thenReturn(List.of());

        scheduler.cancelTimedOutCodOrders();

        verify(orderService, never()).expireCodPending(any(UUID.class));
    }
}
