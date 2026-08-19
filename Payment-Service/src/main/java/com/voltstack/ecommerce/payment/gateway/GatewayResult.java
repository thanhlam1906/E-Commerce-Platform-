package com.voltstack.ecommerce.payment.gateway;

public record GatewayResult(String paymentUrl, String gatewayTxnId) {
}
