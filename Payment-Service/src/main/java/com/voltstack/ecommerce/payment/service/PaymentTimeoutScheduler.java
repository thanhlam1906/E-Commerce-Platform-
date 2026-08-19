package com.voltstack.ecommerce.payment.service;

import com.voltstack.ecommerce.payment.entity.Transaction;
import com.voltstack.ecommerce.payment.entity.TransactionStatus;
import com.voltstack.ecommerce.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * SRS §6: every 60s, expire PENDING transactions older than the timeout → EXPIRED + PaymentTimeoutEvent.
 * Idempotent: the status='PENDING' guard means the event fires at most once per transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final TransactionRepository transactionRepository;
    private final PaymentEventPublisher eventPublisher;

    @Value("${payment.timeout-minutes:15}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${payment.expiry-poll-ms:60000}")
    @Transactional
    public void expirePendingTransactions() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(timeoutMinutes));
        for (Transaction txn : transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, cutoff)) {
            if (transactionRepository.expireIfPending(txn.getId()) == 1) {
                eventPublisher.publishTimeout(txn);
                log.info("Expired pending transaction {}", txn.getId());
            }
        }
    }
}
