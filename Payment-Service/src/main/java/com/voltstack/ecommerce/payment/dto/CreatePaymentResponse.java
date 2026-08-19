package com.voltstack.ecommerce.payment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Bare body for POST /internal/payments. Field names are camelCase to match the existing
 * Order-Service PaymentClient contract (which deserializes directly, no ApiDataResponse envelope).
 */
public record CreatePaymentResponse(UUID transactionId, String paymentUrl, Instant expiresAt) {
}
