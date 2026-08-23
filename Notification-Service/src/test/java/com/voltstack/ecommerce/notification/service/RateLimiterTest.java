package com.voltstack.ecommerce.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private static final String KEY = "throttle:EMAIL:a@b.c";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        rateLimiter = new RateLimiter(redis);
        ReflectionTestUtils.setField(rateLimiter, "maxPerMinute", 5);
        ReflectionTestUtils.setField(rateLimiter, "retryDelaySeconds", 60L);
    }

    @Test
    void underLimit_allowsAndSetsOneMinuteExpiryOnFirstToken() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        assertFalse(rateLimiter.isLimited("a@b.c"));
        verify(ops, never()).decrement(anyString());
    }

    @Test
    void exactlyAtLimit_isAllowed() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(5L);
        assertFalse(rateLimiter.isLimited("a@b.c"));
        verify(ops, never()).decrement(anyString());
    }

    @Test
    void overLimit_delaysAndRefundsTheUnusedToken() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(6L);
        assertTrue(rateLimiter.isLimited("a@b.c"));
        verify(ops).decrement(KEY); // refund so a later retry does not double-count
    }

    @Test
    void redisUnavailable_disablesLimiting() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        assertFalse(rateLimiter.isLimited("a@b.c"));
    }
}
