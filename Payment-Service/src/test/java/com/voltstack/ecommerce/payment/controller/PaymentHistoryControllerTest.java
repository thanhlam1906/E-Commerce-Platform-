package com.voltstack.ecommerce.payment.controller;

import com.voltstack.ecommerce.payment.dto.TransactionResponse;
import com.voltstack.ecommerce.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static final UUID CALLER_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private static final TransactionResponse TXN = new TransactionResponse(
            UUID.fromString("11111111-1111-1111-1111-111111111111"),
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            CALLER_ID,
            new BigDecimal("100.00"), "VND", "CARD", "VNPAY", "SUCCESS",
            "http://pay.example/url", null, Instant.parse("2026-08-19T00:00:00Z"), Instant.parse("2026-08-19T00:00:01Z"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateCaller() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(CALLER_ID.toString(), null, List.of()));
    }

    @Test
    void history_withCaller_returnsPageEnvelope() throws Exception {
        authenticateCaller();
        when(paymentService.history(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(TXN), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/payments/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(TXN.id().toString()))
                .andExpect(jsonPath("$.data.content[0].gateway").value("VNPAY"))
                .andExpect(jsonPath("$.data.content[0].userId").value(CALLER_ID.toString()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void history_forwardsPaginationParams() throws Exception {
        authenticateCaller();
        when(paymentService.history(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 5), 0));

        mockMvc.perform(get("/api/v1/payments/history").param("page", "1").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentService).history(captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(5, captor.getValue().getPageSize());
    }

    @Test
    void history_defaultsToPageSize20() throws Exception {
        authenticateCaller();
        when(paymentService.history(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/payments/history"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(paymentService).history(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(20, captor.getValue().getPageSize());
    }
}
