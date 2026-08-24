package com.voltstack.ecommerce.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.voltstack.ecommerce.payment.exception.GatewayUnavailableException;
import com.voltstack.ecommerce.payment.service.WebhookSignatureVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VnPayGateway (SRS §7): exercises the real HTTP genqr call against a JDK in-process HTTP stub so
 * the QR flow, signing and error mapping are verified without touching the VNPay sandbox.
 */
class VnPayGatewayTest {

    private static final String TMN_CODE = "TEST_TMN";
    private static final String SECRET = "test-vnpay-hash-secret";
    private static final String RETURN_URL = "http://localhost:8080/api/v1/payments/vnpay/return";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startQrServer(Map<String, String> capturedParams) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/paymentv2/vpcpay.html", exchange -> {
            capturedParams.clear();
            capturedParams.putAll(query(exchange.getRequestURI().getRawQuery()));
            String body = "{\"code\":\"00\",\"message\":\"Success\",\"qrcontent\":\"0002010102NAPASTEST\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/paymentv2/vpcpay.html";
    }

    private VnPayGateway gateway(String payUrl) {
        return new VnPayGateway(objectMapper, TMN_CODE, SECRET, payUrl, RETURN_URL, "other", "127.0.0.1", "vn", 15);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static String sign(Map<String, String> params, String secret) throws Exception {
        // Mirror of VnPayCrypto.canonicalize — the test asserts the production signature, so it must
        // use the same rule (sorted key=value, values URL-encoded) or it would "fail" a correct hash.
        String data = params.entrySet().stream()
                .filter(e -> !"vnp_SecureHash".equals(e.getKey()) && !"vnp_SecureHashType".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void createPayment_unconfigured_throwsGatewayUnavailable() {
        VnPayGateway gw = new VnPayGateway(objectMapper, "", "", "http://pay", RETURN_URL, "other", "127.0.0.1", "vn", 15);
        assertThrows(GatewayUnavailableException.class,
                () -> gw.createPayment(UUID.randomUUID(), new BigDecimal("100.00"), "VND", null));
    }

    @Test
    void createPayment_nonVndCurrency_throws() {
        VnPayGateway gw = gateway("http://unused");
        assertThrows(GatewayUnavailableException.class,
                () -> gw.createPayment(UUID.randomUUID(), new BigDecimal("100.00"), "USD", null));
    }

    @Test
    void createPayment_configured_returnsSignedUrlQrAndVerifiedHashes() throws Exception {
        Map<String, String> genQrParams = new HashMap<>();
        String payUrl = startQrServer(genQrParams);
        VnPayGateway gw = gateway(payUrl);
        UUID txnId = UUID.randomUUID();

        GatewayResult result = gw.createPayment(txnId, new BigDecimal("100.00"), "VND", null);

        assertEquals(txnId.toString(), result.gatewayTxnId());
        assertTrue(result.paymentUrl().startsWith(payUrl));
        assertTrue(result.paymentUrl().contains("vnp_TmnCode=" + TMN_CODE));
        assertTrue(result.paymentUrl().contains("vnp_Amount=10000")); // VNPay wire format: VND × 100
        assertTrue(result.qrImage().startsWith("data:image/png;base64,"));

        // genqr request signature must verify under the same hash secret
        assertEquals("genqr", genQrParams.get("vnp_Command"));
        String provided = genQrParams.remove("vnp_SecureHash");
        assertNotNull(provided);
        assertEquals(sign(genQrParams, SECRET), provided);

        // hosted paymentUrl signature must verify too
        Map<String, String> urlParams = query(result.paymentUrl().split("\\?", 2)[1]);
        String urlProvided = urlParams.remove("vnp_SecureHash");
        assertNotNull(urlProvided);
        assertEquals(sign(urlParams, SECRET), urlProvided);
    }

    @Test
    void signVerifyRoundTrip_gatewaySignedParams_passCallbackVerifier() {
        // A VNPay-style callback echoes the same params the gateway signed. VnPayGateway signs and
        // WebhookSignatureVerifier verifies via the shared VnPayCrypto — a signed request must verify.
        Map<String, String> callback = new HashMap<>();
        callback.put("vnp_TmnCode", TMN_CODE);
        callback.put("vnp_Amount", "10000");
        callback.put("vnp_TxnRef", UUID.randomUUID().toString());
        callback.put("vnp_OrderInfo", "Thanh toan don hang 1");
        callback.put("vnp_ReturnUrl", RETURN_URL);
        callback.put("vnp_SecureHash", VnPayCrypto.sign(SECRET, callback));

        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET, "", "", false);
        assertTrue(verifier.verify("VNPAY", new HashMap<>(callback), null));

        // Tampering any signed value must be rejected.
        Map<String, String> tampered = new HashMap<>(callback);
        tampered.put("vnp_Amount", "99999");
        assertTrue(!verifier.verify("VNPAY", new HashMap<String, Object>(tampered), null));
    }

    @Test
    void createPayment_qrServerError_fallsBackToHostedUrlWithoutQr() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/paymentv2/vpcpay.html", exchange -> {
            String body = "{\"code\":\"99\",\"message\":\"System error\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        String payUrl = "http://localhost:" + server.getAddress().getPort() + "/paymentv2/vpcpay.html";

        // QR genqr is best-effort: a QR outage must not abort the hosted payment.
        VnPayGateway gw = gateway(payUrl);
        GatewayResult result = gw.createPayment(UUID.randomUUID(), new BigDecimal("100.00"), "VND", null);

        assertTrue(result.paymentUrl().startsWith(payUrl));
        assertEquals(null, result.qrImage());
    }
}
