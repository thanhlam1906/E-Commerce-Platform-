package com.voltstack.ecommerce.notification.service;

import com.voltstack.ecommerce.notification.domain.NotificationLog;
import com.voltstack.ecommerce.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationQueueTest {

    private static final String REDIS_KEY = "notification:email:jobs";

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private MongoTemplate mongo;
    private NotificationLogRepository repo;
    private NotificationQueue queue;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        mongo = mock(MongoTemplate.class);
        repo = mock(NotificationLogRepository.class);
        queue = new NotificationQueue(redis, mongo, repo);
        ReflectionTestUtils.setField(queue, "redisKey", REDIS_KEY);
    }

    @Test
    void redisClaim_wonZrem_claimsDueJob() {
        ReflectionTestUtils.setField(queue, "queueType", "redis");
        UUID eventId = UUID.randomUUID();
        NotificationLog job = NotificationLog.builder().eventId(eventId).status("PENDING").build();
        when(zset.rangeByScore(eq(REDIS_KEY), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of(eventId.toString()));
        when(zset.remove(REDIS_KEY, eventId.toString())).thenReturn(1L); // this worker won the claim
        when(repo.findByEventId(eventId)).thenReturn(Optional.of(job));

        assertEquals(List.of(job), queue.claimDue(20, 60));
    }

    @Test
    void redisClaim_lostZrem_skipsJobToAvoidDoubleSend() {
        ReflectionTestUtils.setField(queue, "queueType", "redis");
        UUID eventId = UUID.randomUUID();
        when(zset.rangeByScore(eq(REDIS_KEY), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of(eventId.toString()));
        when(zset.remove(REDIS_KEY, eventId.toString())).thenReturn(0L); // another worker already claimed it

        assertTrue(queue.claimDue(20, 60).isEmpty());
        verify(repo, never()).findByEventId(any());
    }

    @Test
    void redisClaim_pendingFilter_skipsNonPendingJob() {
        ReflectionTestUtils.setField(queue, "queueType", "redis");
        UUID eventId = UUID.randomUUID();
        NotificationLog done = NotificationLog.builder().eventId(eventId).status("SENT").build();
        when(zset.rangeByScore(eq(REDIS_KEY), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .thenReturn(Set.of(eventId.toString()));
        when(zset.remove(REDIS_KEY, eventId.toString())).thenReturn(1L);
        when(repo.findByEventId(eventId)).thenReturn(Optional.of(done));

        assertTrue(queue.claimDue(20, 60).isEmpty());
    }

    /**
     * Regression test for a claimMongo bug: two {@code Criteria.where(x).is(null)} conditions
     * made Spring Data MongoDB throw InvalidMongoDbApiUsageException on query build, so the
     * default mongo queue could never claim a job. The query must now build and claim.
     */
    @Test
    void mongoClaim_claimsDuePendingJob() {
        ReflectionTestUtils.setField(queue, "queueType", "mongo");
        NotificationLog job = NotificationLog.builder().eventId(UUID.randomUUID()).status("PENDING").build();
        when(mongo.findAndModify(any(Query.class), any(Update.class),
                any(FindAndModifyOptions.class), eq(NotificationLog.class)))
                .thenReturn(job)
                .thenReturn(null); // second findAndModify finds nothing → claim loop stops

        assertEquals(List.of(job), queue.claimDue(10, 60));
    }
}
