package com.voltstack.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Value("${gateway.security.enabled:true}")
    private boolean securityEnabled;

    /**
     * Public paths — no JWT required.
     * Matches SRS 02-gateway §4.1 whitelist.
     */
    private static final String[] PUBLIC_PATHS = {
        "/health",
        "/actuator/health/**",
        "GET /api/v1/products/**",
        "GET /api/v1/categories/**",
        "POST /api/v1/auth/register",
        "POST /api/v1/auth/login",
        "POST /api/v1/auth/refresh",
        "POST /api/v1/auth/logout",
        "GET /api/v1/auth/verify-email",
        "POST /api/v1/auth/forgot-password",
        "POST /api/v1/auth/reset-password",
        "POST /api/v1/payments/webhook/**"
    };

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        if (!securityEnabled) {
            return http
                .authorizeExchange(ex -> ex.anyExchange().permitAll())
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
        }

        http
            .authorizeExchange(ex -> {
                for (String path : PUBLIC_PATHS) {
                    if (path.startsWith("GET ")) {
                        ex.pathMatchers(org.springframework.http.HttpMethod.GET,
                            path.substring(4)).permitAll();
                    } else if (path.startsWith("POST ")) {
                        ex.pathMatchers(org.springframework.http.HttpMethod.POST,
                            path.substring(5)).permitAll();
                    } else {
                        ex.pathMatchers(path).permitAll();
                    }
                }
                ex.anyExchange().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
            .csrf(ServerHttpSecurity.CsrfSpec::disable);

        return http.build();
    }
}
