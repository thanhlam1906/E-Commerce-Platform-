package com.voltstack.ecommerce.order.service;

import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cancels COD orders left PENDING past the merchant-confirmation window so reserved stock is
 * released instead of being held forever. Only COD — online PENDING expiry is Payment-Service's
 * TIMEOUT event; {@link OrderService#expireCodPending} guards on payment_gateway='COD'.
 * @EnableScheduling is on OrderServiceApplication; no extra scheduling config needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.cod.pending-timeout:30}")
    private long codPendingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${order.cod.poll-delay-ms:60000}")
    public void cancelTimedOutCodOrders() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(codPendingTimeoutMinutes));
        List<Order> pending = orderRepository.findByStatusAndPaymentGatewayAndCreatedAtBefore(
                OrderStatus.PENDING, "COD", cutoff);
        int cancelled = 0;
        for (Order order : pending) {
            if (orderService.expireCodPending(order.getId()) == 1) {
                cancelled++;
            }
        }
        if (cancelled > 0) {
            log.info("COD timeout scheduler cancelled {} PENDING order(s) past confirmation window", cancelled);
        }
    }
}
