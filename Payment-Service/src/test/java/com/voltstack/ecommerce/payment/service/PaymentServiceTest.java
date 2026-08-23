package com.voltstack.ecommerce.payment.service;

import com.voltstack.ecommerce.payment.dto.CreatePaymentRequest;
import com.voltstack.ecommerce.payment.dto.CreatePaymentResponse;
import com.voltstack.ecommerce.payment.dto.TransactionResponse;
import com.voltstack.ecommerce.payment.entity.Gateway;
import com.voltstack.ecommerce.payment.entity.Transaction;
import com.voltstack.ecommerce.payment.entity.TransactionStatus;
import com.voltstack.ecommerce.payment.exception.GatewayUnavailableException;
import com.voltstack.ecommerce.payment.exception.InvalidPaymentStateException;
import com.voltstack.ecommerce.payment.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.payment.exception.WebhookSignatureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.payment.gateway.GatewayResult;
import com.voltstack.ecommerce.payment.gateway.PaymentGateway;
import com.voltstack.ecommerce.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private TransactionRepository transactionRepository;
    private PaymentGateway gateway;
    private WebhookSignatureVerifier signatureVerifier;
    private PaymentEventPublisher eventPublisher;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        gateway = mock(PaymentGateway.class);
        signatureVerifier = mock(WebhookSignatureVerifier.class);
        eventPublisher = mock(PaymentEventPublisher.class);
        // Strategy map: tests run with the SANDBOX bean wired (dev flow). VNPAY bean is exercised in VnPayGatewayTest.
        Map<String, PaymentGateway> gateways = Map.of("SANDBOX", gateway);
        paymentService = new PaymentService(transactionRepository, gateways, signatureVerifier, eventPublisher, new ObjectMapper());
        ReflectionTestUtils.setField(paymentService, "timeoutMinutes", 15L);
        // Sandbox is off by default now; createPayment happy paths run against an explicitly-enabled dev gateway.
        ReflectionTestUtils.setField(paymentService, "sandboxEnabled", true);
    }

    private CreatePaymentRequest request(String gatewayName) {
        return CreatePaymentRequest.builder().orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("VND").paymentMethod("CARD").gateway(gatewayName)
                .returnUrl(null).build();
    }

    private Transaction pendingTxn(String gatewayTxnId) {
        return Transaction.builder().id(UUID.randomUUID()).orderId(UUID.randomUUID()).userId(UUID.randomUUID())
                .amount(new BigDecimal("100.00")).currency("VND").paymentMethod("CARD").gateway(Gateway.VNPAY)
                .status(TransactionStatus.PENDING).gatewayTxnId(gatewayTxnId)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    // ---- createPayment (PAY-002) ----

    @Test
    void createPayment_happyPath_savesPendingUpdatesGatewayAndReturnsResponse() {
        when(gateway.createPayment(any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    UUID txnId = inv.getArgument(0);
                    return new GatewayResult("http://localhost:8084/webhooks/sandbox/" + txnId + "?result=SUCCESS", "SB-" + txnId, null);
                });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(UUID.randomUUID()); // Hibernate assigns the id at persist
            return t;
        });

        CreatePaymentResponse resp = paymentService.createPayment(request("VNPAY"));

        assertNotNull(resp.transactionId());
        assertEquals("http://localhost:8084/webhooks/sandbox/" + resp.transactionId() + "?result=SUCCESS", resp.paymentUrl());
        assertNotNull(resp.expiresAt());
        verify(transactionRepository).save(any(Transaction.class));
        verify(transactionRepository).updateGatewayInfo(eq(resp.transactionId()), anyString(), eq("SB-" + resp.transactionId()));
    }

    @Test
    void createPayment_unsupportedGateway_throwsBadRequest() {
        assertThrows(IllegalArgumentException.class, () -> paymentService.createPayment(request("BITCOIN")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createPayment_sandboxDisabled_throwsBadRequest() {
        ReflectionTestUtils.setField(paymentService, "sandboxEnabled", false);

        assertThrows(IllegalArgumentException.class, () -> paymentService.createPayment(request("VNPAY")));

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createPayment_gatewayFailure_marksExpiredAndThrows502() {
        when(gateway.createPayment(any(), any(), any(), any())).thenThrow(new RuntimeException("down"));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(UUID.randomUUID()); // Hibernate assigns the id at persist
            return t;
        });

        assertThrows(GatewayUnavailableException.class, () -> paymentService.createPayment(request("MOMO")));

        verify(transactionRepository).save(any(Transaction.class));
        verify(transactionRepository).expireIfPending(any(UUID.class));
    }

    // ---- webhook dedup / idempotent (PAY-003) ----

    @Test
    void handleWebhook_success_publishesCompletedExactlyOnce() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);
        Transaction txn = pendingTxn("SB-1");
        when(transactionRepository.findByGatewayTxnId("SB-1")).thenReturn(Optional.of(txn));
        when(transactionRepository.applyWebhookResult(txn.getId(), "SB-1", "{}", "SUCCESS")).thenReturn(1);

        paymentService.handleWebhook("VNPAY", "{}", Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS"), null);
        // Gateway retries the same webhook; the status='PENDING' guard now matches 0 rows.
        when(transactionRepository.applyWebhookResult(txn.getId(), "SB-1", "{}", "SUCCESS")).thenReturn(0);
        paymentService.handleWebhook("VNPAY", "{}", Map.of("gatewayTxnId", "SB-1", "status", "SUCCESS"), null);

        verify(eventPublisher).publishCompleted(txn);
        verify(eventPublisher, never()).publishFailed(any(), any());
        // The guarded update ran twice (retry) but the event fired exactly once (default verify = times(1)).
        verify(transactionRepository, times(2)).applyWebhookResult(any(), any(), any(), any());
    }

    @Test
    void handleWebhook_secondStatusCannotOverrideFirst_guarded() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);
        Transaction txn = pendingTxn("SB-2");
        when(transactionRepository.findByGatewayTxnId("SB-2")).thenReturn(Optional.of(txn));
        // First webhook SUCCESS wins...
        when(transactionRepository.applyWebhookResult(txn.getId(), "SB-2", "{}", "SUCCESS")).thenReturn(1);
        paymentService.handleWebhook("MOMO", "{}", Map.of("gatewayTxnId", "SB-2", "status", "SUCCESS"), null);
        // ...then a FAILED webhook arrives but the guard rejects it (0 rows → no FAILED after SUCCESS).
        when(transactionRepository.applyWebhookResult(txn.getId(), "SB-2", "{}", "FAILED")).thenReturn(0);
        paymentService.handleWebhook("MOMO", "{}", Map.of("gatewayTxnId", "SB-2", "status", "FAILED"), null);

        verify(eventPublisher).publishCompleted(txn);
        verify(eventPublisher, never()).publishFailed(any(), any());
    }

    @Test
    void handleWebhook_lateSuccessAfterExpired_doesNotPublish() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);
        Transaction txn = pendingTxn("SB-LATE");
        when(transactionRepository.findByGatewayTxnId("SB-LATE")).thenReturn(Optional.of(txn));
        // Timeout scheduler already expired it; the guarded SUCCESS update matches 0 rows.
        when(transactionRepository.applyWebhookResult(txn.getId(), "SB-LATE", "{}", "SUCCESS")).thenReturn(0);
        Transaction expired = pendingTxn("SB-LATE");
        expired.setStatus(TransactionStatus.EXPIRED);
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(expired));

        paymentService.handleWebhook("VNPAY", "{}", Map.of("gatewayTxnId", "SB-LATE", "status", "SUCCESS"), null);

        verify(eventPublisher, never()).publishCompleted(any());
        verify(eventPublisher, never()).publishFailed(any(), any());
    }

    @Test
    void handleWebhook_wrongSignature_throwsWithoutStateChange() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(false);

        assertThrows(WebhookSignatureException.class,
                () -> paymentService.handleWebhook("STRIPE", "{}", Map.of("gatewayTxnId", "SB-3", "status", "SUCCESS"), "bad"));

        verify(transactionRepository, never()).findByGatewayTxnId(anyString());
        verify(transactionRepository, never()).applyWebhookResult(any(), any(), any(), any());
    }

    // ---- refund (PAY-005) ----

    @Test
    void refund_success_refundsAndPublishes() {
        Transaction txn = pendingTxn("SB-4");
        txn.setStatus(TransactionStatus.SUCCESS);
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(txn));
        when(transactionRepository.refundIfSuccess(txn.getId(), txn.getAmount())).thenReturn(1);

        paymentService.refund(txn.getId(), txn.getOrderId());

        verify(transactionRepository).refundIfSuccess(txn.getId(), txn.getAmount());
        verify(eventPublisher).publishRefunded(txn);
        assertEquals(txn.getAmount(), txn.getRefundAmount());
    }

    @Test
    void refund_alreadyRefunded_isIdempotentNoOp() {
        Transaction txn = pendingTxn("SB-5");
        txn.setStatus(TransactionStatus.REFUNDED);
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(txn));

        paymentService.refund(txn.getId(), txn.getOrderId());

        verify(transactionRepository, never()).refundIfSuccess(any(), any());
        verify(eventPublisher, never()).publishRefunded(any());
    }

    @Test
    void refund_failedTransaction_throws() {
        Transaction txn = pendingTxn("SB-6");
        txn.setStatus(TransactionStatus.FAILED);
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(txn));

        assertThrows(InvalidPaymentStateException.class, () -> paymentService.refund(txn.getId(), txn.getOrderId()));
        verify(transactionRepository, never()).refundIfSuccess(any(), any());
    }

    // ---- timeout scheduler (PAY-006) ----

    @Test
    void simulateWebhook_failed_publishesFailed() {
        Transaction txn = pendingTxn("SB-7");
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(txn));
        when(transactionRepository.applyWebhookResult(txn.getId(), "SB-7", "{\"simulated\":true,\"transactionId\":\"" + txn.getId() + "\"}", "FAILED")).thenReturn(1);

        paymentService.simulateWebhook(txn.getId(), "FAILED");

        verify(eventPublisher).publishFailed(txn, "Gateway reported FAILED");
        verify(eventPublisher, never()).publishCompleted(any());
    }

    @Test
    void expirePendingTransactions_publishesTimeoutForEachExpired() {
        PaymentTimeoutScheduler scheduler = new PaymentTimeoutScheduler(transactionRepository, eventPublisher);
        ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 15L);
        Transaction txn = pendingTxn("SB-8");
        when(transactionRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(java.util.List.of(txn));
        when(transactionRepository.expireIfPending(txn.getId())).thenReturn(1);

        scheduler.expirePendingTransactions();

        verify(eventPublisher).publishTimeout(txn);
    }

    @Test
    void expirePendingTransactions_skipsWhenTransitionFails() {
        PaymentTimeoutScheduler scheduler = new PaymentTimeoutScheduler(transactionRepository, eventPublisher);
        ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 15L);
        Transaction txn = pendingTxn("SB-9");
        when(transactionRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(java.util.List.of(txn));
        when(transactionRepository.expireIfPending(txn.getId())).thenReturn(0);

        scheduler.expirePendingTransactions();

        verify(eventPublisher, never()).publishTimeout(any());
    }

    @Test
    void expirePendingTransactions_freshPending_doesNothing() {
        PaymentTimeoutScheduler scheduler = new PaymentTimeoutScheduler(transactionRepository, eventPublisher);
        ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 15L);
        when(transactionRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        scheduler.expirePendingTransactions();

        verify(transactionRepository, never()).expireIfPending(any());
        verify(eventPublisher, never()).publishTimeout(any());
    }

    @Test
    void expirePendingTransactions_idempotentRerun_emitsOnce() {
        PaymentTimeoutScheduler scheduler = new PaymentTimeoutScheduler(transactionRepository, eventPublisher);
        ReflectionTestUtils.setField(scheduler, "timeoutMinutes", 15L);
        Transaction txn = pendingTxn("SB-10");
        when(transactionRepository.findByStatusAndCreatedAtBefore(any(), any())).thenReturn(List.of(txn));
        // First run wins the guarded UPDATE; the re-run matches 0 rows and must not emit a second event.
        when(transactionRepository.expireIfPending(txn.getId())).thenReturn(1, 0);

        scheduler.expirePendingTransactions();
        scheduler.expirePendingTransactions();

        verify(eventPublisher, times(1)).publishTimeout(txn);
    }

    @Test
    void refund_raceOnGuard_noEvent() {
        Transaction txn = pendingTxn("SB-11");
        txn.setStatus(TransactionStatus.SUCCESS);
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(txn));
        // A concurrent refund won the guarded UPDATE; this call is a no-op.
        when(transactionRepository.refundIfSuccess(txn.getId(), txn.getAmount())).thenReturn(0);

        paymentService.refund(txn.getId(), txn.getOrderId());

        verify(eventPublisher, never()).publishRefunded(any());
    }

    @Test
    void handleVnPayNotify_success_amountMatches_publishesCompleted() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);
        Transaction txn = pendingTxn("SB-AMT");
        when(transactionRepository.findByGatewayTxnId("SB-AMT")).thenReturn(Optional.of(txn));
        when(transactionRepository.applyWebhookResult(any(), any(), anyString(), eq("SUCCESS"))).thenReturn(1);

        paymentService.handleVnPayNotify(Map.of(
                "vnp_TxnRef", "SB-AMT", "vnp_TransactionStatus", "00", "vnp_Amount", "10000"));

        verify(eventPublisher).publishCompleted(txn);
    }

    @Test
    void handleVnPayNotify_amountMismatch_rejectsWithoutConfirming() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);
        Transaction txn = pendingTxn("SB-AMT2"); // amount 100.00 VND → expected 10000 on the wire
        when(transactionRepository.findByGatewayTxnId("SB-AMT2")).thenReturn(Optional.of(txn));

        assertThrows(IllegalArgumentException.class, () -> paymentService.handleVnPayNotify(Map.of(
                "vnp_TxnRef", "SB-AMT2", "vnp_TransactionStatus", "00", "vnp_Amount", "12345")));

        verify(transactionRepository, never()).applyWebhookResult(any(), any(), any(), any());
        verify(eventPublisher, never()).publishCompleted(any());
    }

    @Test
    void handleWebhook_unknownGatewayTxnId_throwsNotFound() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);
        when(transactionRepository.findByGatewayTxnId("NOPE")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.handleWebhook("VNPAY", "{}", Map.of("gatewayTxnId", "NOPE", "status", "SUCCESS"), null));

        verify(transactionRepository, never()).applyWebhookResult(any(), any(), any(), any());
    }

    @Test
    void handleWebhook_missingGatewayTxnId_throwsBadRequest() {
        when(signatureVerifier.verify(anyString(), any(), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> paymentService.handleWebhook("VNPAY", "{}", Map.of("status", "SUCCESS"), null));

        verify(transactionRepository, never()).findByGatewayTxnId(any());
    }

    @Test
    void history_returnsOnlyCallerTransactions() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
        try {
            Pageable pageable = PageRequest.of(0, 20);
            Transaction txn = pendingTxn("SB-12");
            when(transactionRepository.findByUserId(userId, pageable))
                    .thenReturn(new PageImpl<>(List.of(txn), pageable, 1));

            Page<TransactionResponse> page = paymentService.history(pageable);

            assertEquals(1, page.getTotalElements());
            assertEquals(txn.getId(), page.getContent().get(0).id());
            verify(transactionRepository).findByUserId(userId, pageable);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void history_withoutAuth_throwsNotFound() {
        SecurityContextHolder.clearContext();

        assertThrows(ResourceNotFoundException.class, () -> paymentService.history(PageRequest.of(0, 20)));
        verify(transactionRepository, never()).findByUserId(any(), any());
    }
}
