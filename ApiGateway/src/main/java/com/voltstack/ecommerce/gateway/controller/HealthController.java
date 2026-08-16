package com.voltstack.ecommerce.gateway.controller;

import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GW-006: aggregate health. Reports each upstream service's state from the
 * Eureka registry (UP if >=1 instance registered, else DOWN).
 *
 * ponytail: registry-based, not live HTTP liveness probes. Upgrade to WebClient
 * calls to each service's /actuator/health when services exist in Phase 2.
 */
@RestController
public class HealthController {

    private static final List<String> SERVICES = List.of(
        "Product-Catalog-Service", "identity-service", "order-service",
        "payment-service", "notification-service");

    private final ReactiveDiscoveryClient discoveryClient;

    public HealthController(ReactiveDiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    @GetMapping("/health")
    public Mono<Map<String, Object>> health() {
        return Flux.fromIterable(SERVICES)
            .flatMap(service -> discoveryClient.getInstances(service)
                .collectList()
                .map(instances -> Map.entry(service, instances.isEmpty() ? "DOWN" : "UP")))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .map(services -> {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("status", services.containsValue("DOWN") ? "DOWN" : "UP");
                body.put("services", services);
                body.put("timestamp", Instant.now().toString());
                return body;
            });
    }
}
