package com.voltstack.ecommerce.payment.repository;

import com.voltstack.ecommerce.payment.entity.Transaction;
import com.voltstack.ecommerce.payment.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByGatewayTxnId(String gatewayTxnId);

    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    List<Transaction> findByStatusAndCreatedAtBefore(TransactionStatus status, Instant createdAt);

    /** Gateway createPayment succeeded: store the payment_url + gateway_txn_id. The status='PENDING' guard
     *  makes it safe outside a method-level transaction: the timeout scheduler may have expired the row already. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transactions SET payment_url = :paymentUrl, gateway_txn_id = :gatewayTxnId, updated_at = now() WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int updateGatewayInfo(@Param("id") UUID id, @Param("paymentUrl") String paymentUrl, @Param("gatewayTxnId") String gatewayTxnId);

    /**
     * Webhook result. The status='PENDING' guard is the dedup: a webhook arriving after the
     * transaction already moved (SUCCESS/FAILED/EXPIRED/REFUNDED) touches 0 rows → idempotent no-op.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transactions SET status = :newStatus, gateway_txn_id = :gatewayTxnId, raw_webhook = :rawWebhook::jsonb, updated_at = now() WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int applyWebhookResult(@Param("id") UUID id, @Param("gatewayTxnId") String gatewayTxnId,
                           @Param("rawWebhook") String rawWebhook, @Param("newStatus") String newStatus);

    /** Gateway createPayment failed → no orphan PENDING. Also used by the timeout scheduler. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transactions SET status = 'EXPIRED', updated_at = now() WHERE id = :id AND status = 'PENDING'", nativeQuery = true)
    int expireIfPending(@Param("id") UUID id);

    /** Refund: only SUCCESS can be refunded, and only once. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE transactions SET status = 'REFUNDED', refund_amount = :amount, updated_at = now() WHERE id = :id AND status = 'SUCCESS'", nativeQuery = true)
    int refundIfSuccess(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
