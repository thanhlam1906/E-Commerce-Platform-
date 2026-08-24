package com.voltstack.ecommerce.payment.security;

import com.voltstack.ecommerce.payment.config.SecurityConfig;
import com.voltstack.ecommerce.payment.controller.InternalPaymentController;
import com.voltstack.ecommerce.payment.controller.PaymentHistoryController;
import com.voltstack.ecommerce.payment.controller.VnPayController;
import com.voltstack.ecommerce.payment.controller.WebhookController;
import com.voltstack.ecommerce.payment.dto.CreatePaymentRequest;
import com.voltstack.ecommerce.payment.dto.CreatePaymentResponse;
import com.voltstack.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint-level security (SRS §4 / internal-auth). Builds MockMvc with only the Spring Security
 * chain so HeaderAuthFilter/InternalTokenFilter run exactly once via SecurityConfig, and sets
 * servletPath explicitly because MockMvc leaves it empty while the filters key off it.
 */
@WebMvcTest(controllers = {WebhookController.class, InternalPaymentController.class, PaymentHistoryController.class, VnPayController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"internal.service-token=test-token", "payment.sandbox.enabled=true"})
class SecurityFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static final String VALID_PAYMENT_BODY = "{\"orderId\":\"22222222-2222-2222-2222-222222222222\""
            + ",\"userId\":\"33333333-3333-3333-3333-333333333333\",\"amount\":100.00"
            + ",\"currency\":\"VND\",\"paymentMethod\":\"CARD\",\"gateway\":\"VNPAY\"}";
    private static final String WEBHOOK_BODY = "{\"gatewayTxnId\":\"SB-1\",\"status\":\"SUCCESS\"}";

    @TestConfiguration
    static class SecurityFilterTestConfig {
        @Bean
        MockMvc mockMvc(WebApplicationContext wac) {
            return MockMvcBuilders.webAppContextSetup(wac)
                    .apply(springSecurity())
                    .build();
        }
    }

    private static RequestPostProcessor servletPath(String path) {
        return request -> {
            request.setServletPath(path);
            return request;
        };
    }

    // ---- /internal/** requires the shared bearer token ----

    @Test
    void internalPayments_withoutToken_isRejected() throws Exception {
        mockMvc.perform(post("/internal/payments")
                        .with(servletPath("/internal/payments"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(paymentService);
    }

    @Test
    void internalPayments_withWrongToken_isRejected() throws Exception {
        mockMvc.perform(post("/internal/payments")
                        .with(servletPath("/internal/payments"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(paymentService);
    }

    @Test
    void internalPayments_withCorrectToken_reachesController() throws Exception {
        when(paymentService.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(new CreatePaymentResponse(UUID.randomUUID(), "http://pay.example/url", Instant.now(), null));

        mockMvc.perform(post("/internal/payments")
                        .with(servletPath("/internal/payments"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PAYMENT_BODY))
                .andExpect(status().isOk());

        verify(paymentService).createPayment(any(CreatePaymentRequest.class));
    }

    @Test
    void internalRefund_withoutToken_isRejected() throws Exception {
        mockMvc.perform(post("/internal/payments/{txnId}/refund", UUID.randomUUID())
                        .with(servletPath("/internal/payments/11111111-1111-1111-1111-111111111111/refund"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"22222222-2222-2222-2222-222222222222\"}"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(paymentService);
    }

    // ---- /webhooks/** and /actuator/health are public ----

    @Test
    void webhook_withoutToken_isPublic() throws Exception {
        mockMvc.perform(post("/webhooks/VNPAY")
                        .with(servletPath("/webhooks/VNPAY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WEBHOOK_BODY))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhook(any(), any(), any(), any());
    }

    // ---- /webhooks/sandbox/** requires the shared bearer token (dev simulator) ----

    @Test
    void sandbox_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/webhooks/sandbox/{transactionId}", UUID.randomUUID())
                        .with(servletPath("/webhooks/sandbox/11111111-1111-1111-1111-111111111111"))
                        .param("result", "SUCCESS"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(paymentService);
    }

    @Test
    void sandbox_withWrongToken_isRejected() throws Exception {
        mockMvc.perform(get("/webhooks/sandbox/{transactionId}", UUID.randomUUID())
                        .with(servletPath("/webhooks/sandbox/11111111-1111-1111-1111-111111111111"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong")
                        .param("result", "SUCCESS"))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(paymentService);
    }

    @Test
    void sandbox_withCorrectToken_reachesController() throws Exception {
        mockMvc.perform(get("/webhooks/sandbox/{transactionId}", UUID.randomUUID())
                        .with(servletPath("/webhooks/sandbox/11111111-1111-1111-1111-111111111111"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer test-token")
                        .param("result", "SUCCESS"))
                .andExpect(status().isOk());

        verify(paymentService).simulateWebhook(any(), any());
    }

    @Test
    void actuatorHealth_withoutToken_isPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .with(servletPath("/actuator/health")))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(401, status, "health should not require auth");
                    assertNotEquals(403, status, "health should not require auth");
                });
    }

    // ---- VNPay IPN + return URL are public (server-to-server / browser redirect) ----

    @Test
    void vnpayReturn_withoutToken_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .with(servletPath("/api/v1/payments/vnpay/return"))
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "abc"))
                .andExpect(status().isOk());

        verify(paymentService).handleVnPayNotify(any());
    }

    @Test
    void vnpayIpn_withoutToken_isPublic() throws Exception {
        mockMvc.perform(get("/webhooks/vnpay/ipn")
                        .with(servletPath("/webhooks/vnpay/ipn"))
                        .param("vnp_TxnRef", "11111111-1111-1111-1111-111111111111"))
                .andExpect(status().isOk());

        verify(paymentService).handleVnPayNotify(any());
    }

    // ---- history requires the gateway-injected X-User-Id ----

    @Test
    void history_withoutUserId_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/payments/history")
                        .with(servletPath("/api/v1/payments/history")))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(paymentService);
    }

    @Test
    void history_withUserId_reachesController() throws Exception {
        UUID caller = UUID.randomUUID();
        when(paymentService.history(any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/payments/history")
                        .with(servletPath("/api/v1/payments/history"))
                        .header("X-User-Id", caller.toString()))
                .andExpect(status().isOk());

        verify(paymentService).history(any());
    }
}
