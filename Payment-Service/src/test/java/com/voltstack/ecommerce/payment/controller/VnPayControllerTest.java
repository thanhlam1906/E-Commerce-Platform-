package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.payment.exception.WebhookSignatureException;
import com.voltstack.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * VNPay callbacks (SRS §4). VNPay sends query params, so these endpoints take no JSON body and must
 * always answer 200 (the RspCode tells VNPay whether to retry).
 */
@WebMvcTest(VnPayController.class)
@AutoConfigureMockMvc(addFilters = false)
class VnPayControllerTest {

    private static final String TXN_REF = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    // ---- IPN (server-to-server) ----

    @Test
    void ipn_valid_answersConfirmSuccess() throws Exception {
        mockMvc.perform(get("/webhooks/vnpay/ipn")
                        .param("vnp_TxnRef", TXN_REF)
                        .param("vnp_TransactionStatus", "00")
                        .param("vnp_SecureHash", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        verify(paymentService).handleVnPayNotify(anyMap());
    }

    @Test
    void ipn_badSignature_answersRspCode97() throws Exception {
        doThrow(new WebhookSignatureException("bad"))
                .when(paymentService).handleVnPayNotify(anyMap());

        mockMvc.perform(get("/webhooks/vnpay/ipn").param("vnp_TxnRef", TXN_REF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));
    }

    @Test
    void ipn_unknownOrder_answersRspCode01() throws Exception {
        doThrow(new ResourceNotFoundException("not found"))
                .when(paymentService).handleVnPayNotify(anyMap());

        mockMvc.perform(post("/webhooks/vnpay/ipn").param("vnp_TxnRef", TXN_REF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("01"));
    }

    @Test
    void ipn_runtimeError_answersRspCode99() throws Exception {
        doThrow(new IllegalStateException("boom"))
                .when(paymentService).handleVnPayNotify(anyMap());

        mockMvc.perform(get("/webhooks/vnpay/ipn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"));
    }

    // ---- return URL (browser redirect) ----

    @Test
    void returnUrl_success_showsSuccessPage() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_OrderInfo", "Thanh toan don hang 1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUCCESS")));
    }

    @Test
    void returnUrl_cancelled_showsCancelledPage() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_ResponseCode", "24"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CANCELLED")));
    }

    @Test
    void returnUrl_badSignature_showsInvalidPage() throws Exception {
        doThrow(new WebhookSignatureException("bad"))
                .when(paymentService).handleVnPayNotify(anyMap());

        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_ResponseCode", "00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("INVALID")));
    }

    @Test
    void returnUrl_escapesOrderInfo_htmlInjection() throws Exception {
        mockMvc.perform(get("/api/v1/payments/vnpay/return")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_OrderInfo", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>"))));
    }
}
