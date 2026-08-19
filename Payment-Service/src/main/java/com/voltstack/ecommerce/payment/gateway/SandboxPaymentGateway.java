package com.voltstack.ecommerce.payment.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deterministic, no-network sandbox gateway (SRS §7). The payment URL points back at our own
 * webhook sandbox simulator so a developer (or a browser redirect) can complete the flow manually.
 */
@Slf4j
@Component
public class SandboxPaymentGateway implements PaymentGateway {

    private final String returnUrlBase;

    public SandboxPaymentGateway(@Value("${payment.sandbox.return-url-base:http://localhost:8084}") String returnUrlBase) {
        this.returnUrlBase = returnUrlBase;
    }

    @Override
    public GatewayResult createPayment(UUID transactionId, BigDecimal amount, String currency, String returnUrl) {
        String paymentUrl = returnUrlBase + "/webhooks/sandbox/" + transactionId + "?result=SUCCESS";
        String gatewayTxnId = "SB-" + transactionId;
        log.info("Sandbox payment created: transactionId={}, amount={} {}, gatewayTxnId={}", transactionId, amount, currency, gatewayTxnId);
        return new GatewayResult(paymentUrl, gatewayTxnId);
    }
}
