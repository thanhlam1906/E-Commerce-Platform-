package com.voltstack.ecommerce.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Runs after Spring Security (WebFilter default order > security chain order).
 * Copies JWT claims into X-User-Id / X-User-Roles before forwarding.
 * Always overwrites — downstream must not trust client-set versions.
 *
 * SRS 02-gateway §8: public (no-token) requests get empty headers.
 */
@Component
public class ClaimsPropagationFilter implements WebFilter {

    private final String internalSecret;

    public ClaimsPropagationFilter(@Value("${internal.service-token:}") String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(auth -> enrichRequest(exchange, auth))
            .switchIfEmpty(Mono.defer(() -> Mono.just(enrichRequest(exchange, null))))
            .flatMap(chain::filter);
    }

    private ServerWebExchange enrichRequest(ServerWebExchange exchange, Authentication auth) {
        String userId = "";
        String roles = "";
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            userId = jwt.getClaimAsString("sub");
            List<String> roleList = jwt.getClaimAsStringList("roles");
            roles = roleList != null ? String.join(",", roleList) : "";
        }

        ServerHttpRequest.Builder builder = exchange.getRequest().mutate()
            .header("X-User-Id", userId != null ? userId : "")
            .header("X-User-Roles", roles);
        if (internalSecret != null && !internalSecret.isBlank()) {
            builder.header("X-Internal-Secret", internalSecret);
        }
        return exchange.mutate().request(builder.build()).build();
    }
}
