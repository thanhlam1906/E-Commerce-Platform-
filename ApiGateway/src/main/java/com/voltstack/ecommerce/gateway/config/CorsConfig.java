package com.voltstack.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS config. Origins whitelisted via env var CORS_ORIGINS.
 * SRS 02-gateway §5: no wildcard + credentials, restricted headers only.
 *
 * Runs before the security filter chain (CorsWebFilter is not Ordered by
 * default) so valid preflights short-circuit instead of hitting 401.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    @Value("${gateway.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Authorization", "Content-Type",
            "X-User-Id", "X-User-Roles", "Idempotency-Key"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
