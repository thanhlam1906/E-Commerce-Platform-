package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.dto.CreatePaymentRequest;
import com.voltstack.ecommerce.payment.dto.CreatePaymentResponse;
import com.voltstack.ecommerce.payment.exception.GatewayUnavailableException;
import com.voltstack.ecommerce.payment.exception.InvalidPaymentStateException;
import com.voltstack.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static final String ORDER_ID = "22222222-2222-2222-2222-222222222222";
    private static final String USER_ID = "33333333-3333-3333-3333-333333333333";
    private static final UUID TXN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String VALID_BODY = "{\"orderId\":\"" + ORDER_ID + "\",\"userId\":\"" + USER_ID
            + "\",\"amount\":100.00,\"currency\":\"VND\",\"paymentMethod\":\"CARD\",\"gateway\":\"VNPAY\"}";

    @Test
    void createPayment_valid_returns200WithContract() throws Exception {
        when(paymentService.createPayment(any(CreatePaymentRequest.class)))
                .thenReturn(new CreatePaymentResponse(TXN_ID, "http://pay.example/url", Instant.parse("2026-08-19T00:00:00Z")));

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(TXN_ID.toString()))
                .andExpect(jsonPath("$.paymentUrl").value("http://pay.example/url"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-19T00:00:00Z"));

        ArgumentCaptor<CreatePaymentRequest> captor = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(paymentService).createPayment(captor.capture());
        assertEquals(ORDER_ID, captor.getValue().getOrderId().toString());
        assertEquals(USER_ID, captor.getValue().getUserId().toString());
        assertEquals(0, new BigDecimal("100.00").compareTo(captor.getValue().getAmount()));
        assertEquals("VND", captor.getValue().getCurrency());
        assertEquals("CARD", captor.getValue().getPaymentMethod());
        assertEquals("VNPAY", captor.getValue().getGateway());
    }

    @Test
    void createPayment_amountZero_returns400() throws Exception {
        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("100.00", "0")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_amountNegative_returns400() throws Exception {
        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("100.00", "-5.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_badCurrency_returns400() throws Exception {
        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"VND\"", "\"VNDD\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_missingGateway_returns400() throws Exception {
        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace(",\"gateway\":\"VNPAY\"", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_unsupportedGateway_returns400() throws Exception {
        doThrow(new IllegalArgumentException("Cổng thanh toán không được hỗ trợ: BITCOIN"))
                .when(paymentService).createPayment(any(CreatePaymentRequest.class));

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY.replace("\"VNPAY\"", "\"BITCOIN\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createPayment_gatewayFailure_returns502() throws Exception {
        doThrow(new GatewayUnavailableException("Cổng thanh toán không khả dụng"))
                .when(paymentService).createPayment(any(CreatePaymentRequest.class));

        mockMvc.perform(post("/internal/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    void refund_success_returns200() throws Exception {
        mockMvc.perform(post("/internal/payments/{txnId}/refund", TXN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\"}"))
                .andExpect(status().isOk());

        verify(paymentService).refund(TXN_ID, UUID.fromString(ORDER_ID));
    }

    @Test
    void refund_nonSuccessTxn_returns409() throws Exception {
        doThrow(new InvalidPaymentStateException("Chỉ hoàn tiền được cho giao dịch SUCCESS"))
                .when(paymentService).refund(TXN_ID, UUID.fromString(ORDER_ID));

        mockMvc.perform(post("/internal/payments/{txnId}/refund", TXN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void refund_alreadyRefunded_returns200Idempotent() throws Exception {
        mockMvc.perform(post("/internal/payments/{txnId}/refund", TXN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + ORDER_ID + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void refund_missingOrderId_returns400() throws Exception {
        mockMvc.perform(post("/internal/payments/{txnId}/refund", TXN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
