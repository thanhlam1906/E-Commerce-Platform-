package com.voltstack.ecommerce.payment.gateway;

/**
 * Result of calling a gateway. {@code qrImage} is a base64 PNG data URI (e.g. VNPay QR) rendered
 * server-side for inline display; null when the gateway only offers a hosted page.
 */
public record GatewayResult(String paymentUrl, String gatewayTxnId, String qrImage) {
}

