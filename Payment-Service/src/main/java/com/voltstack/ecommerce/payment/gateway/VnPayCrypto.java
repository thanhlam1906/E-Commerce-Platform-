package com.voltstack.ecommerce.payment.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * VNPay HMAC-SHA512 checksum (official algorithm, Techspec 2.1.0). The hash string is the sorted
 * {@code key=value} list joined by {@code &}, each value URL-encoded (form encoding, space → '+'),
 * excluding the signature fields. Used by both VnPayGateway (to sign requests) and
 * WebhookSignatureVerifier (to verify IPN / return-URL callbacks) so the two can never diverge.
 */
public final class VnPayCrypto {

    private static final String ALGORITHM = "HmacSHA512";
    private static final Set<String> SIGNATURE_KEYS = Set.of("vnp_SecureHash", "vnp_SecureHashType");

    private VnPayCrypto() {
    }

    /** Canonical string VNPay signs: sorted key=value, values URL-encoded, signature fields excluded. */
    public static String canonicalize(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(e -> !SIGNATURE_KEYS.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static String sign(String secret, Map<String, String> params) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(canonicalize(params).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA512 không khả dụng", e);
        }
    }

    public static boolean verify(String secret, Map<String, String> params) {
        String provided = params.get("vnp_SecureHash");
        if (provided == null || provided.isBlank()) {
            return false;
        }
        String computed = sign(secret, params);
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
    }
}
