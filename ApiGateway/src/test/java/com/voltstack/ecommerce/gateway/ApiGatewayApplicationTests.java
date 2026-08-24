package com.voltstack.ecommerce.gateway;

import com.voltstack.ecommerce.gateway.filter.ClaimsPropagationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "eureka.client.enabled=false")
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    void propagatesClaimsFromJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
            .claim("roles", List.of("CUSTOMER", "ORDER_ADMIN"))
            .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/orders").build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        new ClaimsPropagationFilter("test-secret")
            .filter(exchange, exc -> {
                captured.set(exc);
                return Mono.empty();
            })
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(jwt, List.of())))
            .block();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Id"))
            .isEqualTo("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d");
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Roles"))
            .isEqualTo("CUSTOMER,ORDER_ADMIN");
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Internal-Secret"))
            .isEqualTo("test-secret");
    }

    @Test
    void setsEmptyHeadersWithoutToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/products").build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        new ClaimsPropagationFilter("test-secret")
            .filter(exchange, exc -> {
                captured.set(exc);
                return Mono.empty();
            })
            .block();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Id")).isEmpty();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-User-Roles")).isEmpty();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Internal-Secret"))
            .isEqualTo("test-secret");
    }

    @Test
    void doesNotInjectSecretWhenBlank() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/products").build());

        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        new ClaimsPropagationFilter("")
            .filter(exchange, exc -> {
                captured.set(exc);
                return Mono.empty();
            })
            .block();

        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Internal-Secret")).isNull();
    }
}
