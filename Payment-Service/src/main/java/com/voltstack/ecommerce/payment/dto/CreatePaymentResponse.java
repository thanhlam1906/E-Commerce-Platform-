package com.voltstack.ecommerce.payment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Bare body for POST /internal/payments. Field names are camelCase to match the existing
 * Order-Service PaymentClient contract (which deserializes directly, no ApiDataResponse envelope).
 *
 * @param qrImage base64 PNG data URI of the NAPAS QR (VNPay), null when the gateway only offers a
 *                hosted page — Order passes it through so the client can render "quét mã QR" inline.
 */
public record CreatePaymentResponse(UUID transactionId, String paymentUrl, Instant expiresAt, String qrImage) {
}
