package com.voltstack.ecommerce.payment.service;

import com.voltstack.ecommerce.payment.gateway.VnPayCrypto;
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
    void verify_validVnpaySignature_usesSha512UrlEncoded() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(VNPAY_SECRET, "", "", false);
        // VNPay carries the signature as vnp_SecureHash over URL-encoded canonical params (shared VnPayCrypto).
        Map<String, Object> payload = Map.of(
                "gatewayTxnId", "SB-1",
                "status", "SUCCESS",
                "vnp_SecureHash", VnPayCrypto.sign(VNPAY_SECRET, Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS")));
        assertTrue(verifier.verify("VNPAY", payload, null));
    }

    /** A VNPay return callback built with the doc's Java hash (sorted params, HMAC-SHA512, URL-encoded
     *  values — VnPayCrypto.canonicalize/sign) must verify, and a tampered vnp_Amount must be rejected. */
    @Test
    void vnpayReturnCallback_signedWithDocJavaHash_verifiesAndRejectsTamperedAmount() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(VNPAY_SECRET, "", "", false);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", "2799000000");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_BankTranNo", "VNPAY0810");
        params.put("vnp_CardType", "ATM");
        params.put("vnp_OrderInfo", "Thanh toan don hang 82df99dc-60fb-4f20-b7b7-25cd9fc8054b");
        params.put("vnp_PayDate", "20260823140000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", "4HHH08ZK");
        params.put("vnp_TransactionNo", "12345678");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "82df99dc-60fb-4f20-b7b7-25cd9fc8054b");
        params.put("vnp_SecureHash", VnPayCrypto.sign(VNPAY_SECRET, params));

        assertTrue(verifier.verify("VNPAY", new LinkedHashMap<>(params), null));

        Map<String, String> tampered = new LinkedHashMap<>(params);
        tampered.put("vnp_Amount", "2799000001"); // attacker rewrites the amount after signing
        assertFalse(verifier.verify("VNPAY", new LinkedHashMap<>(tampered), null));
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
