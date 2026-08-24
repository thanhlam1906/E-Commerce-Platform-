package com.voltstack.ecommerce.payment.service;

import com.voltstack.ecommerce.payment.gateway.VnPayCrypto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifies webhook HMAC signatures (SRS §4): VNPay = HMAC-SHA512, MoMo + Stripe = HMAC-SHA256.
 * The signed string is the sorted key=value list of the JSON payload minus signature fields.
 * Fail closed: when a gateway has no secret the webhook is rejected unless
 * payment.webhook.allow-unsigned is explicitly enabled (dev-only opt-in).
 * Real VNPay/MoMo/Stripe field-level canonicalisation and header parsing are credential-gated follow-ups.
 */
@Slf4j
@Component
public class WebhookSignatureVerifier {

    private static final String VNPAY = "VNPAY";
    private static final Set<String> SIGNATURE_KEYS = Set.of("signature", "vnp_SecureHash", "vnp_SecureHashType");

    private final Map<String, String> secrets;
    private final boolean allowUnsigned;

    public WebhookSignatureVerifier(@Value("${payment.gateway-secrets.vnpay:}") String vnpaySecret,
                                    @Value("${payment.gateway-secrets.momo:}") String momoSecret,
                                    @Value("${payment.gateway-secrets.stripe:}") String stripeSecret,
                                    @Value("${payment.webhook.allow-unsigned:false}") boolean allowUnsigned) {
        this.secrets = Map.of("VNPAY", vnpaySecret, "MOMO", momoSecret, "STRIPE", stripeSecret);
        this.allowUnsigned = allowUnsigned;
    }

    public boolean verify(String gateway, Map<String, Object> payload, String headerSignature) {
        String secret = secrets.get(gateway);
        if (secret == null || secret.isBlank()) {
            if (allowUnsigned) {
                log.warn("No HMAC secret configured for {}, accepting webhook unsigned (dev opt-in)", gateway);
                return true;
            }
            return false;
        }
        String provided = extractSignature(payload, headerSignature);
        if (provided == null || provided.isBlank()) {
            return false;
        }
        if (VNPAY.equals(gateway)) {
            // VNPay canonicalisation URL-encodes values; VnPayCrypto is shared with VnPayGateway so
            // request signing and callback verification can never diverge (Techspec 2.1.0).
            return VnPayCrypto.verify(secret, toStringMap(payload));
        }
        String canonical = payload.entrySet().stream()
                .filter(e -> !SIGNATURE_KEYS.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String computed = hmac("HmacSHA256", secret, canonical);
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> toStringMap(Map<String, Object> payload) {
        Map<String, String> out = new java.util.HashMap<>();
        payload.forEach((k, v) -> out.put(k, v == null ? "" : v.toString()));
        return out;
    }

    private String extractSignature(Map<String, Object> payload, String headerSignature) {
        Object bodySig = payload.get("signature");
        if (bodySig != null) {
            return bodySig.toString();
        }
        Object vnpaySig = payload.get("vnp_SecureHash");
        if (vnpaySig != null) {
            return vnpaySig.toString();
        }
        return headerSignature;
    }

    private String hmac(String algorithm, String secret, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC " + algorithm + " unavailable", e);
        }
    }
}
