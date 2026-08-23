package com.voltstack.ecommerce.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.voltstack.ecommerce.payment.exception.GatewayUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * VNPay gateway adapter (SRS §7). Uses the VNPay {@code genqr} command to get a NAPAS dynamic QR
 * payload, then renders it as a PNG data URI so the merchant page can show "quét mã QR" inline
 * after checkout; {@code paymentUrl} is the hosted VNPay page as a browser fallback.
 *
 * <p>Credential-gated: throws {@link GatewayUnavailableException} until {@code payment.vnpay.tmn-code}
 * and {@code payment.gateway-secrets.vnpay} are set (env: VNPAY_TMN_CODE / VNPAY_SECRET).
 */
@Slf4j
@Component("VNPAY")
public class VnPayGateway implements PaymentGateway {

    private static final String VNP_VERSION = "2.1.0";
    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VNP_TZ = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Duration GATEWAY_TIMEOUT = Duration.ofSeconds(5);
    private static final int QR_SIZE = 300;

    private final ObjectMapper objectMapper;
    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String defaultReturnUrl;
    private final String orderType;
    private final String ipAddr;
    private final String locale;
    private final long timeoutMinutes;

    public VnPayGateway(ObjectMapper objectMapper,
                        @Value("${payment.vnpay.tmn-code:}") String tmnCode,
                        @Value("${payment.gateway-secrets.vnpay:}") String hashSecret,
                        @Value("${payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
                        @Value("${payment.vnpay.return-url:http://localhost:8080/api/v1/payments/vnpay/return}") String defaultReturnUrl,
                        @Value("${payment.vnpay.order-type:other}") String orderType,
                        @Value("${payment.vnpay.ip-addr:127.0.0.1}") String ipAddr,
                        @Value("${payment.vnpay.locale:vn}") String locale,
                        @Value("${payment.timeout-minutes:15}") long timeoutMinutes) {
        this.objectMapper = objectMapper;
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.defaultReturnUrl = defaultReturnUrl;
        this.orderType = orderType;
        this.ipAddr = ipAddr;
        this.locale = locale;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Override
    public GatewayResult createPayment(UUID transactionId, BigDecimal amount, String currency, String returnUrl) {
        if (!isConfigured()) {
            throw new GatewayUnavailableException("VNPay chưa được cấu hình (thiếu vnp_TmnCode hoặc hash secret)");
        }
        if (!"VND".equalsIgnoreCase(currency)) {
            throw new GatewayUnavailableException("VNPay chỉ hỗ trợ VND, nhận được: " + currency);
        }
        long amountCents = amount.multiply(BigDecimal.valueOf(100)).longValueExact();
        String txnRef = transactionId.toString();
        LocalDateTime now = LocalDateTime.now(VNP_TZ);
        String createDate = VNP_TIME.format(now);
        String expireDate = VNP_TIME.format(now.plusMinutes(timeoutMinutes));
        String redirectUrl = (returnUrl == null || returnUrl.isBlank()) ? defaultReturnUrl : returnUrl;
        String orderInfo = "Thanh toan don hang " + txnRef;

        String hostedUrl = buildSignedUrl("pay", txnRef, amountCents, orderInfo, createDate, expireDate, redirectUrl);
        // QR is best-effort UX — a genqr outage must not abort an otherwise valid hosted payment.
        String qrImage = null;
        try {
            qrImage = encodeQr(genQrContent(txnRef, amountCents, orderInfo, createDate, expireDate, redirectUrl));
        } catch (RuntimeException e) {
            log.warn("VNPay genqr không khả dụng, fallback về hosted URL: {}", e.getMessage());
        }

        log.info("VNPay QR payment created: txnRef={}, amountCents={}, qrImage={}", txnRef, amountCents, qrImage != null);
        return new GatewayResult(hostedUrl, txnRef, qrImage);
    }

    // ---- VNPay genqr: returns the NAPAS QR payload, not a URL ----

    private String genQrContent(String txnRef, long amountCents, String orderInfo,
                                String createDate, String expireDate, String returnUrl) {
        Map<String, String> params = baseParams(txnRef, amountCents, orderInfo, createDate, expireDate, returnUrl);
        params.put("vnp_Command", "genqr");
        params.put("vnp_SecureHash", VnPayCrypto.sign(hashSecret, params));
        String url = payUrl + "?" + buildQuery(params);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(GATEWAY_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(GATEWAY_TIMEOUT).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new GatewayUnavailableException("VNPay genqr HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            if (!"00".equals(json.path("code").asText())) {
                throw new GatewayUnavailableException("VNPay genqr thất bại: code=" + json.path("code").asText()
                        + ", message=" + json.path("message").asText());
            }
            return json.path("qrcontent").asText();
        } catch (IOException e) {
            throw new GatewayUnavailableException("Không thể gọi VNPay genqr: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayUnavailableException("VNPay genqr bị gián đoạn: " + e.getMessage());
        }
    }

    private String buildSignedUrl(String command, String txnRef, long amountCents, String orderInfo,
                                  String createDate, String expireDate, String returnUrl) {
        Map<String, String> params = baseParams(txnRef, amountCents, orderInfo, createDate, expireDate, returnUrl);
        params.put("vnp_Command", command);
        params.put("vnp_SecureHash", VnPayCrypto.sign(hashSecret, params));
        return payUrl + "?" + buildQuery(params);
    }

    private Map<String, String> baseParams(String txnRef, long amountCents, String orderInfo,
                                           String createDate, String expireDate, String returnUrl) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("vnp_Version", VNP_VERSION);
        p.put("vnp_Command", "pay"); // overwritten by caller
        p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", String.valueOf(amountCents));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", orderInfo);
        p.put("vnp_OrderType", orderType);
        p.put("vnp_Locale", locale);
        p.put("vnp_ReturnUrl", returnUrl);
        p.put("vnp_IpAddr", ipAddr);
        p.put("vnp_CreateDate", createDate);
        p.put("vnp_ExpireDate", expireDate);
        return p;
    }

    private String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encodeQr(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            // QR render failure should not kill an otherwise valid payment — fall back to hosted URL only.
            log.warn("Không render được mã QR VNPay, fallback về hosted URL: {}", e.getMessage());
            return null;
        }
    }

    private boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank() && hashSecret != null && !hashSecret.isBlank();
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
