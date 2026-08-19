package com.voltstack.ecommerce.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/** Fixed 1-minute window: max {@code maxPerMinute} emails per recipient. Redis key throttle:EMAIL:{recipient}. */
@Slf4j
@Component
public class RateLimiter {

    /** Atomic INCR + EXPIRE-on-first-token. Split calls could leave a key with no TTL and throttle forever. */
    private static final String INCR_AND_EXPIRE =
            "local c = redis.call('INCR', KEYS[1]) " +
            "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return c";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrAndExpireScript;

    @Value("${notification.rate-limit.max-per-minute:5}")
    private int maxPerMinute;

    @Value("${notification.rate-limit.retry-delay-seconds:60}")
    private long retryDelaySeconds;

    public RateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrAndExpireScript = new DefaultRedisScript<>(INCR_AND_EXPIRE, Long.class);
    }

    public long retryDelaySeconds() {
        return retryDelaySeconds;
    }

    /**
     * @return true when the recipient is over the per-minute budget (the job must be delayed, not dropped).
     */
    public boolean isLimited(String recipient) {
        String key = "throttle:EMAIL:" + recipient;
        try {
            Long count = redisTemplate.execute(incrAndExpireScript, List.of(key), 60);
            if (count != null && count > maxPerMinute) {
                redisTemplate.opsForValue().decrement(key); // refund the token we are not spending
                return true;
            }
            return false;
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, rate limiting disabled for {}", recipient);
            return false;
        }
    }
}
