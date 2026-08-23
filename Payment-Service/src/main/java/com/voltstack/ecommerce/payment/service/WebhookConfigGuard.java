package com.voltstack.ecommerce.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to boot when unsigned webhooks are allowed with no gateway secret configured —
 * that combination would re-open the unsigned-SUCCESS hole (every gateway rejects fail-closed
 * except the allow-unsigned dev opt-in). The opt-in is only meaningful against a real secret.
 */
@Component
public class WebhookConfigGuard {

    public WebhookConfigGuard(@Value("${payment.webhook.allow-unsigned:false}") boolean allowUnsigned,
                              @Value("${payment.gateway-secrets.vnpay:}") String vnpaySecret,
                              @Value("${payment.gateway-secrets.momo:}") String momoSecret,
                              @Value("${payment.gateway-secrets.stripe:}") String stripeSecret) {
        if (allowUnsigned && vnpaySecret.isBlank() && momoSecret.isBlank() && stripeSecret.isBlank()) {
            throw new IllegalStateException(
                    "payment.webhook.allow-unsigned=true requires at least one gateway secret configured");
        }
    }
}
