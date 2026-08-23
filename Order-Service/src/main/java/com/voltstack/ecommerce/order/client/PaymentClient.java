package com.voltstack.ecommerce.order.client;

import com.voltstack.ecommerce.order.constant.ErrorMessages;
import com.voltstack.ecommerce.order.exception.PaymentUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous REST call to Payment-Service (SRS §11). Any failure fails the checkout cleanly.
 */
@Component
public class PaymentClient {

    private final RestClient restClient;
    private final String internalToken;

    public PaymentClient(@Value("${payment-service.base-url}") String baseUrl,
                         @Value("${internal.service-token}") String internalToken) {
        // 5s connect/read so a silently-black-holed Payment service cannot block the checkout
        // thread (or, on refund paths, hold the orders row lock) indefinitely.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    public PaymentResult createPayment(UUID orderId, UUID userId, BigDecimal amount, String currency,
                                       String paymentMethod, String gateway, String returnUrl) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("orderId", orderId);
            body.put("userId", userId);
            body.put("amount", amount);
            body.put("currency", currency);
            body.put("paymentMethod", paymentMethod);
            body.put("gateway", gateway);
            if (returnUrl != null && !returnUrl.isBlank()) {
                body.put("returnUrl", returnUrl);
            }
            PaymentResponse resp = restClient.post()
                    .uri("/internal/payments")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(PaymentResponse.class);
            if (resp == null || resp.transactionId() == null || resp.paymentUrl() == null) {
                throw new PaymentUnavailableException("Payment service trả về dữ liệu không hợp lệ");
            }
            return new PaymentResult(resp.transactionId(), resp.paymentUrl(), resp.expiresAt(), resp.qrImage());
        } catch (RestClientException e) {
            throw new PaymentUnavailableException("Không thể kết nối tới Payment service: " + e.getMessage());
        }
    }

    public void refund(UUID orderId, UUID transactionId) {
        try {
            restClient.post()
                    .uri("/internal/payments/{txnId}/refund", transactionId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("orderId", orderId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new PaymentUnavailableException("Không thể gọi hoàn tiền: " + e.getMessage());
        }
    }

    public record PaymentResult(UUID transactionId, String paymentUrl, Instant expiresAt, String qrImage) {}

    private record PaymentResponse(UUID transactionId, String paymentUrl, Instant expiresAt, String qrImage) {}
}
