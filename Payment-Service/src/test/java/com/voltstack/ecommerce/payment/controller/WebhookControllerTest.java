package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.payment.exception.WebhookSignatureException;
import com.voltstack.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebhookController webhookController;

    @MockitoBean
    private PaymentService paymentService;

    @BeforeEach
    void resetSandboxFlag() {
        // Sandbox is off by default; ensure a test that enabled it doesn't leak into the next one.
        ReflectionTestUtils.setField(webhookController, "sandboxEnabled", false);
    }

    private static final String SUCCESS_BODY = "{\"gatewayTxnId\":\"SB-1\",\"status\":\"SUCCESS\"}";
    private static final String FAILED_BODY = "{\"gatewayTxnId\":\"SB-2\",\"status\":\"FAILED\"}";

    @Test
    void handleGateway_validSuccess_returns200AndDelegates() throws Exception {
        mockMvc.perform(post("/webhooks/VNPAY")
                        .header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_BODY))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhook(eq("VNPAY"), eq(SUCCESS_BODY),
                eq(Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS")), eq("sig"));
    }

    @Test
    void handleGateway_validFailed_returns200() throws Exception {
        mockMvc.perform(post("/webhooks/MOMO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FAILED_BODY))
                .andExpect(status().isOk());

        verify(paymentService).handleWebhook(eq("MOMO"), eq(FAILED_BODY),
                eq(Map.of("gatewayTxnId", "SB-2", "status", "FAILED")), isNull());
    }

    @Test
    void handleGateway_duplicateRetry_returns200EachTime() throws Exception {
        mockMvc.perform(post("/webhooks/MOMO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(post("/webhooks/MOMO")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_BODY))
                .andExpect(status().isOk());

        // Dedup/state-change is the service's job; the controller must answer 200 to both.
        verify(paymentService, times(2)).handleWebhook(eq("MOMO"), eq(SUCCESS_BODY), anyMap(), isNull());
    }

    @Test
    void handleGateway_badSignature_returns401() throws Exception {
        doThrow(new WebhookSignatureException("Chữ ký webhook không hợp lệ"))
                .when(paymentService).handleWebhook(any(), any(), any(), any());

        mockMvc.perform(post("/webhooks/STRIPE")
                        .header("Stripe-Signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void handleGateway_unsupportedGateway_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Cổng thanh toán không được hỗ trợ: BTC"))
                .when(paymentService).handleWebhook(any(), any(), any(), any());

        mockMvc.perform(post("/webhooks/BTC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void handleGateway_unknownTransaction_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Không tìm thấy giao dịch"))
                .when(paymentService).handleWebhook(any(), any(), any(), any());

        mockMvc.perform(post("/webhooks/VNPAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SUCCESS_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void handleGateway_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/webhooks/VNPAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
        verify(paymentService, times(0)).handleWebhook(any(), any(), any(), any());
    }

    @Test
    void handleGateway_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/webhooks/VNPAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sandbox_offByDefault_returns404AndDoesNotDelegate() throws Exception {
        UUID txnId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        mockMvc.perform(get("/webhooks/sandbox/{transactionId}", txnId)
                        .param("result", "SUCCESS"))
                .andExpect(status().isNotFound());

        verify(paymentService, never()).simulateWebhook(any(), any());
    }

    @Test
    void sandbox_simulatesResult_whenExplicitlyEnabled_returns200() throws Exception {
        UUID txnId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ReflectionTestUtils.setField(webhookController, "sandboxEnabled", true);

        mockMvc.perform(get("/webhooks/sandbox/{transactionId}", txnId)
                        .param("result", "SUCCESS"))
                .andExpect(status().isOk());

        verify(paymentService).simulateWebhook(txnId, "SUCCESS");
    }

    @Test
    void handleGateway_bodyOver256kb_returns413WithoutDelegating() throws Exception {
        byte[] big = new byte[300 * 1024];
        Arrays.fill(big, (byte) 'a');

        mockMvc.perform(post("/webhooks/VNPAY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(big))
                .andExpect(status().isPayloadTooLarge());

        verify(paymentService, never()).handleWebhook(any(), any(), any(), any());
    }
}
