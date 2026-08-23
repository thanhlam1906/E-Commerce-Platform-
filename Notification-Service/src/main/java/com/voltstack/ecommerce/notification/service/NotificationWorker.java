package com.voltstack.ecommerce.notification.service;

import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.exception.PermanentFailureException;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled worker: claims due jobs, rate-limits, renders, sends and owns the
 * NOT-007 retry state (attempts + next_retry_at) inside the MongoDB job document.
 */
@Slf4j
@Component
public class NotificationWorker {

    private final NotificationQueue queue;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailRenderer renderer;
    private final EmailSender emailSender;
    private final DeadLetterWriter deadLetterWriter;
    private final RateLimiter rateLimiter;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${notification.retry.first-delay-seconds:30}")
    private long firstDelaySeconds;

    @Value("${notification.retry.second-delay-seconds:60}")
    private long secondDelaySeconds;

    @Value("${notification.worker.lease-seconds:60}")
    private int leaseSeconds;

    @Value("${notification.worker.batch-size:20}")
    private int batchSize;

    public NotificationWorker(NotificationQueue queue,
                              NotificationLogRepository notificationLogRepository,
                              EmailRenderer renderer,
                              EmailSender emailSender,
                              DeadLetterWriter deadLetterWriter,
                              RateLimiter rateLimiter) {
        this.queue = queue;
        this.notificationLogRepository = notificationLogRepository;
        this.renderer = renderer;
        this.emailSender = emailSender;
        this.deadLetterWriter = deadLetterWriter;
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(fixedDelayString = "${notification.worker.fixed-delay-ms:1000}")
    public void poll() {
        List<NotificationLog> jobs = queue.claimDue(batchSize, leaseSeconds);
        for (NotificationLog job : jobs) {
            try {
                process(job);
            } catch (Exception e) {
                log.error("Unexpected error processing notification eventId={}", job.getEventId(), e);
            }
        }
    }

    private void process(NotificationLog job) {
        // Over the per-recipient budget: delay the job, do not count it as a send attempt.
        if (rateLimiter.isLimited(job.getRecipient())) {
            reschedule(job, attempts(job), rateLimiter.retryDelaySeconds());
            return;
        }
        EmailRenderer.RenderedEmail email;
        try {
            email = renderer.render(job.getTemplate(), job.getPayload());
        } catch (PermanentFailureException e) {
            deadLetterWriter.record(job, e.getMessage(), true);
            queue.remove(job.getEventId());
            return;
        }
        // ponytail: a crash between send() and save(SENT) leaves the job PENDING; with multiple
        // worker instances it is re-claimed after the lease expires and the email is sent twice.
        // Provider-side dedup key or a heartbeated lease removes this (see NotificationQueue javadoc).
        try {
            String providerMsgId = emailSender.send(job.getRecipient(), job.getEventId().toString(), job.getTemplate(), email);
            job.setStatus("SENT");
            job.setAttempts(attempts(job) + 1);
            job.setSentAt(Instant.now());
            job.setProviderMsgId(providerMsgId);
            job.setError(null);
            job.setLeaseUntil(null);
            job.setNextRetryAt(null);
            notificationLogRepository.save(job);
            queue.remove(job.getEventId());
            log.info("Email sent recipient={} template={} eventId={} attempts={}",
                    job.getRecipient(), job.getTemplate(), job.getEventId(), job.getAttempts());
        } catch (RuntimeException e) {
            int attempts = attempts(job) + 1;
            if (attempts >= maxAttempts) {
                deadLetterWriter.record(job, e.getMessage(), false);
                queue.remove(job.getEventId());
            } else {
                long delaySeconds = attempts == 1 ? firstDelaySeconds : secondDelaySeconds;
                reschedule(job, attempts, delaySeconds);
            }
        }
    }

    private void reschedule(NotificationLog job, int attempts, long delaySeconds) {
        job.setAttempts(attempts);
        job.setNextRetryAt(Instant.now().plusSeconds(delaySeconds));
        job.setLeaseUntil(null);
        notificationLogRepository.save(job);
        queue.reschedule(job.getEventId(), job.getNextRetryAt());
        log.info("Notification eventId={} rescheduled attempt {} in {}s", job.getEventId(), attempts, delaySeconds);
    }

    private static int attempts(NotificationLog job) {
        return job.getAttempts() == null ? 0 : job.getAttempts();
    }
}
