package com.voltstack.ecommerce.notification.service;

import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Job scheduling for the worker. The durable source of truth is always the MongoDB
 * {@code notification_logs} document; the queue only schedules when a job is due.
 *
 * <p>{@code mongo} (default, local): atomic find-and-modify lease on PENDING documents.
 * {@code redis} (production): a sorted set of event ids keyed by due-time (epoch ms),
 * while retry state (attempts + next_retry_at) stays in the MongoDB job.
 *
 * <p>ponytail: the mongo lease is set once and never heartbeated, so a send slower than the
 * lease (or a crash between send and status-save) can be re-claimed and emailed twice. Needs
 * provider-side idempotency (SendGrid dedup key) before running multiple instances.
 * ponytail: in redis mode a crash between the dedup insert and the zset enqueue orphans a
 * PENDING row the zset worker never sees; needs a reconciliation scan (PENDING + due, not in zset).
 */
@Slf4j
@Component
public class NotificationQueue {

    private final StringRedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final NotificationLogRepository notificationLogRepository;

    @Value("${notification.queue.type:mongo}")
    private String queueType;

    @Value("${notification.queue.redis-key:notification:email:jobs}")
    private String redisKey;

    public NotificationQueue(StringRedisTemplate redisTemplate, MongoTemplate mongoTemplate,
                             NotificationLogRepository notificationLogRepository) {
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.notificationLogRepository = notificationLogRepository;
    }

    public void enqueue(UUID eventId, Instant dueAt) {
        if (isRedis()) {
            redisTemplate.opsForZSet().add(redisKey, eventId.toString(), dueAt.toEpochMilli());
        }
    }

    public void reschedule(UUID eventId, Instant dueAt) {
        if (isRedis()) {
            redisTemplate.opsForZSet().add(redisKey, eventId.toString(), dueAt.toEpochMilli());
        }
    }

    public void remove(UUID eventId) {
        if (isRedis()) {
            redisTemplate.opsForZSet().remove(redisKey, eventId.toString());
        }
    }

    public List<NotificationLog> claimDue(int batchSize, int leaseSeconds) {
        return isRedis() ? claimRedis(batchSize) : claimMongo(batchSize, leaseSeconds);
    }

    private boolean isRedis() {
        return "redis".equalsIgnoreCase(queueType);
    }

    private List<NotificationLog> claimMongo(int batchSize, int leaseSeconds) {
        Instant now = Instant.now();
        List<NotificationLog> jobs = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            Query query = new Query();
            query.addCriteria(Criteria.where("status").is("PENDING"));
            // exists(false): a fresh insert omits these fields, so they match as "no lease /
            // not yet scheduled". Two Criteria.where(x).is(null) would throw
            // InvalidMongoDbApiUsageException when the query document is built.
            query.addCriteria(new Criteria().andOperator(
                    new Criteria().orOperator(
                            Criteria.where("lease_until").lte(now),
                            Criteria.where("lease_until").exists(false)),
                    new Criteria().orOperator(
                            Criteria.where("next_retry_at").lte(now),
                            Criteria.where("next_retry_at").exists(false))));
            query.with(Sort.by(Sort.Direction.ASC, "next_retry_at"));
            query.limit(1);
            Update update = new Update().set("lease_until", now.plusSeconds(leaseSeconds));
            NotificationLog job = mongoTemplate.findAndModify(query, update,
                    FindAndModifyOptions.options().returnNew(true), NotificationLog.class);
            if (job == null) {
                break;
            }
            jobs.add(job);
        }
        return jobs;
    }

    private List<NotificationLog> claimRedis(int batchSize) {
        Set<String> due = redisTemplate.opsForZSet()
                .rangeByScore(redisKey, 0, Instant.now().toEpochMilli(), 0, batchSize);
        if (due == null || due.isEmpty()) {
            return List.of();
        }
        List<NotificationLog> jobs = new ArrayList<>();
        for (String id : due) {
            // zrem is the atomic claim: only one worker wins per event id.
            if (redisTemplate.opsForZSet().remove(redisKey, id) == 1) {
                try {
                    UUID eventId = UUID.fromString(id);
                    notificationLogRepository.findByEventId(eventId)
                            .filter(job -> "PENDING".equals(job.getStatus()))
                            .ifPresent(jobs::add);
                } catch (IllegalArgumentException ignored) {
                    // Non-UUID member in the queue; drop it.
                } catch (RuntimeException e) {
                    // Claimed from the zset but the Mongo lookup failed; put it back so it is
                    // not lost, and keep the rest of the batch (already claimed ids stay in jobs).
                    redisTemplate.opsForZSet().add(redisKey, id, Instant.now().toEpochMilli());
                    log.warn("Re-scheduled queue member {} after Mongo lookup failure", id, e);
                }
            }
        }
        return jobs;
    }
}
