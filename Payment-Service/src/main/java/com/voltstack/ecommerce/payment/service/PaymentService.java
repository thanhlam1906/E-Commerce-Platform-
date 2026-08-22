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
import com.voltstack.ecommerce.payment.gateway.GatewayResult;
import com.voltstack.ecommerce.payment.gateway.PaymentGateway;
import com.voltstack.ecommerce.payment.repository.TransactionRepository;
import com.voltstack.ecommerce.payment.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final PaymentGateway gateway;
    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentEventPublisher eventPublisher;

    @Value("${payment.timeout-minutes:15}")
    private long timeoutMinutes;

    @Value("${payment.sandbox.enabled:false}")
    private boolean sandboxEnabled = false;

    // ---- PAY-002: synchronous create (SRS §3) ----
    // No @Transactional on purpose: each repository call commits on its own, so a failed
    // gateway call can mark EXPIRED, and a failed updateGatewayInfo leaves the PENDING row
    // for the webhook/timeout to resolve. A method-level tx would roll the row back instead.

    public CreatePaymentResponse createPayment(CreatePaymentRequest req) {
        Gateway gatewayName = resolveGateway(req.getGateway());
        if (!sandboxEnabled) {
            // Sandbox is the only gateway impl today; with it disabled there is no selectable gateway.
            throw new IllegalArgumentException("Cổng thanh toán không được hỗ trợ: " + req.getGateway());
        }
        Instant now = Instant.now();
        Transaction txn = Transaction.builder()
                .orderId(req.getOrderId()).userId(req.getUserId())
                .amount(req.getAmount()).currency(req.getCurrency())
                .paymentMethod(req.getPaymentMethod()).gateway(gatewayName)
                .status(TransactionStatus.PENDING).createdAt(now).updatedAt(now)
                .build();
        transactionRepository.save(txn);

        GatewayResult result;
        try {
            result = gateway.createPayment(txn.getId(), req.getAmount(), req.getCurrency(), req.getReturnUrl());
        } catch (RuntimeException e) {
            // Only expire when the gateway call itself failed. A failed updateGatewayInfo after a
            // successful gateway call must leave the txn PENDING for the webhook/timeout to resolve.
            try {
                transactionRepository.expireIfPending(txn.getId());
            } catch (RuntimeException ex) {
                log.warn("Failed to expire orphaned transaction {}", txn.getId(), ex);
            }
            throw new GatewayUnavailableException("Cổng thanh toán không khả dụng: " + e.getMessage());
        }
        if (transactionRepository.updateGatewayInfo(txn.getId(), result.paymentUrl(), result.gatewayTxnId()) == 0) {
            log.warn("Payment {} already moved (timeout expired it) before gateway info was stored", txn.getId());
        }
        return new CreatePaymentResponse(txn.getId(), result.paymentUrl(), now.plus(Duration.ofMinutes(timeoutMinutes)));
    }

    // ---- PAY-003: webhook (SRS §4) ----

    @Transactional
    public void handleWebhook(String gatewayName, String rawBody, Map<String, Object> payload, String headerSignature) {
        String gateway = gatewayName.toUpperCase();
        resolveGateway(gateway);
        if (!signatureVerifier.verify(gateway, payload, headerSignature)) {
            throw new WebhookSignatureException("Chữ ký webhook không hợp lệ");
        }
        String gatewayTxnId = stringField(payload, "gatewayTxnId", "vnp_TxnRef");
        if (gatewayTxnId == null || gatewayTxnId.isBlank()) {
            throw new IllegalArgumentException("Thiếu gatewayTxnId trong webhook");
        }
        Transaction txn = transactionRepository.findByGatewayTxnId(gatewayTxnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch với gateway_txn_id: " + gatewayTxnId));
        String status = stringField(payload, "status", "vnp_ResponseCode");
        applyResult(txn, status, rawBody);
    }

    /** Sandbox simulator (dev tool): applies SUCCESS/FAILED directly, no signature required. */
    @Transactional
    public void simulateWebhook(UUID transactionId, String result) {
        Transaction txn = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + transactionId));
        String raw = "{\"simulated\":true,\"transactionId\":\"" + transactionId + "\"}";
        applyResult(txn, result, raw);
    }

    private void applyResult(Transaction txn, String statusRaw, String rawWebhook) {
        TransactionStatus newStatus = switch (statusRaw == null ? "" : statusRaw.toUpperCase()) {
            case "SUCCESS", "00", "0" -> TransactionStatus.SUCCESS;
            case "FAILED" -> TransactionStatus.FAILED;
            default -> throw new IllegalArgumentException("Trạng thái webhook không hợp lệ: " + statusRaw);
        };
        int rows = transactionRepository.applyWebhookResult(txn.getId(), txn.getGatewayTxnId(), rawWebhook, newStatus.name());
        if (rows == 1) {
            if (newStatus == TransactionStatus.SUCCESS) {
                eventPublisher.publishCompleted(txn);
            } else {
                eventPublisher.publishFailed(txn, "Gateway reported FAILED");
            }
        } else if (newStatus == TransactionStatus.SUCCESS) {
            Transaction current = transactionRepository.findById(txn.getId()).orElse(null);
            if (current != null && isTerminalNonSuccess(current.getStatus())) {
                log.error("Late SUCCESS webhook after {} — manual reconciliation needed (PAY-006): orderId={}, transactionId={}, amount={}",
                        current.getStatus(), txn.getOrderId(), txn.getId(), txn.getAmount());
            } else {
                log.info("Webhook already handled for txn {}, skipping (idempotent)", txn.getId());
            }
        } else {
            log.info("Webhook already handled for txn {}, skipping (idempotent)", txn.getId());
        }
    }

    private boolean isTerminalNonSuccess(TransactionStatus status) {
        return status == TransactionStatus.EXPIRED
                || status == TransactionStatus.FAILED
                || status == TransactionStatus.REFUNDED;
    }

    // ---- PAY-005: refund ----

    @Transactional
    public void refund(UUID txnId, UUID orderId) {
        Transaction txn = transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch: " + txnId));
        if (!txn.getOrderId().equals(orderId)) {
            throw new IllegalArgumentException("orderId không khớp với giao dịch");
        }
        if (txn.getStatus() == TransactionStatus.REFUNDED) {
            log.info("Refund already processed for txn {}, skipping (idempotent)", txnId);
            return;
        }
        if (txn.getStatus() != TransactionStatus.SUCCESS) {
            throw new InvalidPaymentStateException("Chỉ hoàn tiền được cho giao dịch SUCCESS");
        }
        if (transactionRepository.refundIfSuccess(txnId, txn.getAmount()) == 1) {
            txn.setRefundAmount(txn.getAmount());
            eventPublisher.publishRefunded(txn);
        }
    }

    // ---- PAY-007: user-scoped history ----

    @Transactional(readOnly = true)
    public Page<TransactionResponse> history(Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        return transactionRepository.findByUserId(userId, pageable).map(TransactionResponse::from);
    }

    // ---- helpers ----

    private Gateway resolveGateway(String name) {
        try {
            return Gateway.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cổng thanh toán không được hỗ trợ: " + name);
        }
    }

    private String stringField(Map<String, Object> payload, String primary, String alias) {
        Object v = payload.get(primary);
        if (v == null) {
            v = payload.get(alias);
        }
        return v == null ? null : v.toString();
    }
}
