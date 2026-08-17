package com.voltstack.ecommerce.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limiting key resolvers. Redis-backed limits are declared per-route in
 * application.yml (RequestRateLimiter filter). RedisRateLimiter fails OPEN when
 * Redis is unreachable, so dev keeps working without Redis.
 *
 * SRS 02-gateway §6: general 100 req/s/IP, payments 20 req/s/IP, login 5 req/min/email.
 *
 * ponytail: login uses IP key (1/s, burst 5). Email-based key requires reading the
 * request body, which consumes it and breaks forwarding — needs body caching.
 * Add emailKeyResolver when identity-service + Redis provisioning land in Phase 2.
 */
@Configuration(proxyBeanMethods = false)
public class RateLimiterConfig {

    /** Default key: client IP */
    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown");
    }
}
