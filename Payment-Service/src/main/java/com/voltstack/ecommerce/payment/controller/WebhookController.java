package com.voltstack.ecommerce.payment.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook entry points (SRS §4). Real gateways POST to /webhooks/{gateway} and must pass HMAC
 * verification. /webhooks/sandbox/{transactionId} is a dev-only simulator (default off) that
 * requires the internal.service-token bearer and applies the result directly so a developer can
 * complete the flow with a curl.
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    /** Reject webhook bodies above 256 KB before JSON parsing / HMAC work (DoS guard). */
    private static final long MAX_WEBHOOK_BODY_BYTES = 256L * 1024;

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${payment.sandbox.enabled:false}")
    private boolean sandboxEnabled;

    @PostMapping("/{gateway}")
    public ResponseEntity<Void> handleGateway(@PathVariable String gateway,
                                              @RequestBody(required = false) String rawBody,
                                              HttpServletRequest request) {
        // getContentLengthLong() is -1 for chunked bodies, so the header check alone can be bypassed;
        // also guard on the actual parsed body size.
        if (request.getContentLengthLong() > MAX_WEBHOOK_BODY_BYTES
                || (rawBody != null && rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_WEBHOOK_BODY_BYTES)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }
        if (rawBody == null || rawBody.isBlank()) {
            throw new IllegalArgumentException("Webhook body không được để trống");
        }
        try {
            Map<String, Object> payload = objectMapper.readValue(rawBody, new TypeReference<>() {
            });
            paymentService.handleWebhook(gateway, rawBody, payload, request.getHeader("Stripe-Signature"));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Webhook body không phải JSON hợp lệ");
        }
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = "/sandbox/{transactionId}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> sandbox(@PathVariable UUID transactionId, @RequestParam String result) {
        if (!sandboxEnabled) {
            return ResponseEntity.notFound().build();
        }
        paymentService.simulateWebhook(transactionId, result);
        return ResponseEntity.ok("<html><body><h2>Payment " + result.toUpperCase() + "</h2>"
                + "<p>Transaction " + transactionId + "</p></body></html>");
    }
}
