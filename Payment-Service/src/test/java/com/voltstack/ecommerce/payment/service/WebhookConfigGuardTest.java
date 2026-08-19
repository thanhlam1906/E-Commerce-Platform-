package com.voltstack.ecommerce.payment.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebhookConfigGuardTest {

    @Test
    void allowUnsignedWithNoSecrets_refusesToBoot() {
        assertThrows(IllegalStateException.class, () -> new WebhookConfigGuard(true, "", "", ""));
    }

    @Test
    void allowUnsignedWithOneSecret_boots() {
        assertDoesNotThrow(() -> new WebhookConfigGuard(true, "vnpay-secret", "", ""));
    }

    @Test
    void allowUnsignedDisabledNoSecrets_boots() {
        assertDoesNotThrow(() -> new WebhookConfigGuard(false, "", "", ""));
    }
}
