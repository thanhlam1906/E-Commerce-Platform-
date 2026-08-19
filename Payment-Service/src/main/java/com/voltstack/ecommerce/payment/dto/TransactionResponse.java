package com.voltstack.ecommerce.payment.dto;

import com.voltstack.ecommerce.payment.entity.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String gateway,
        String status,
        String paymentUrl,
        BigDecimal refundAmount,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(t.getId(), t.getOrderId(), t.getUserId(), t.getAmount(),
                t.getCurrency(), t.getPaymentMethod(), t.getGateway().name(), t.getStatus().name(),
                t.getPaymentUrl(), t.getRefundAmount(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
