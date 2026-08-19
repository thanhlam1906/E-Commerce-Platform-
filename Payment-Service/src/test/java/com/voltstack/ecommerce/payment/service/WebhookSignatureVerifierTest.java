package com.voltstack.ecommerce.payment.service;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureVerifierTest {

    private static final String MOMO_SECRET = "momo-secret";
    private static final String VNPAY_SECRET = "vnpay-secret";

    private String hmac(String algorithm, String secret, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> payloadWithSignature(String gateway, String secret) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("gatewayTxnId", "SB-1");
        payload.put("status", "SUCCESS");
        String canonical = "gatewayTxnId=SB-1&status=SUCCESS";
        String algorithm = "VNPAY".equals(gateway) ? "HmacSHA512" : "HmacSHA256";
        payload.put("signature", hmac(algorithm, secret, canonical));
        return payload;
    }

    @Test
    void verify_validMomoSignature_accepted() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("", MOMO_SECRET, "", false);
        assertTrue(verifier.verify("MOMO", payloadWithSignature("MOMO", MOMO_SECRET), null));
    }

    @Test
    void verify_tamperedMomoSignature_rejected() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("", MOMO_SECRET, "", false);
        Map<String, Object> payload = payloadWithSignature("MOMO", MOMO_SECRET);
        payload.put("status", "FAILED"); // tampered after signing
        assertFalse(verifier.verify("MOMO", payload, null));
    }

    @Test
    void verify_validVnpaySignature_usesSha512() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(VNPAY_SECRET, "", "", false);
        assertTrue(verifier.verify("VNPAY", payloadWithSignature("VNPAY", VNPAY_SECRET), null));
    }

    @Test
    void verify_noSecretConfigured_rejectsUnsignedByDefault() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("", "", "", false);
        Map<String, Object> payload = Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS");
        assertFalse(verifier.verify("STRIPE", payload, null));
    }

    @Test
    void verify_noSecretConfigured_allowUnsigned_accepts() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("", "", "", true);
        Map<String, Object> payload = Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS");
        assertTrue(verifier.verify("STRIPE", payload, null));
    }

    @Test
    void verify_missingSignatureWithSecret_rejected() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("", MOMO_SECRET, "", false);
        assertFalse(verifier.verify("MOMO", Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS"), null));
    }
}
