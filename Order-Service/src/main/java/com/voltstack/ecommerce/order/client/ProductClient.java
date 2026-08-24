package com.voltstack.ecommerce.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.grpc.ProductVerifyServiceGrpc;
import com.voltstack.ecommerce.grpc.SkuRequest;
import com.voltstack.ecommerce.grpc.VerifySkuResponse;
import com.voltstack.ecommerce.order.constant.ErrorMessages;
import com.voltstack.ecommerce.order.exception.ProductUnavailableException;
import com.voltstack.ecommerce.order.exception.SkuNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * gRPC to Product-Catalog with Redis snapshot-cache fallback per SRS §11.
 * Cache key {@code product:snapshot:{sku}}, refreshed on every successful gRPC call.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private static final String CACHE_KEY = "product:snapshot:";
    /** M3: bound each call so a hung Product service cannot block the checkout thread forever. */
    private static final long GRPC_DEADLINE_SECONDS = 5;

    @GrpcClient("product-verify")
    private ProductVerifyServiceGrpc.ProductVerifyServiceBlockingStub productStub;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${order.product-cache-ttl-minutes:60}")
    private long cacheTtlMinutes;

    /**
     * @return product snapshot, or throws SkuNotFoundException (409) when the SKU does not exist
     *         and ProductUnavailableException (503) when the product source is unreachable.
     */
    public ProductSnapshot getSnapshot(String sku) {
        try {
            VerifySkuResponse resp = productStub.withDeadlineAfter(GRPC_DEADLINE_SECONDS, TimeUnit.SECONDS)
                    .verifySku(SkuRequest.newBuilder().setSku(sku).build());
            if (!resp.getExists()) {
                throw new SkuNotFoundException(ErrorMessages.SKU_NOT_FOUND + ": " + sku);
            }
            ProductSnapshot snap = ProductSnapshot.fromGrpc(resp.getSnapshot());
            cache(sku, snap);
            return snap;
        } catch (SkuNotFoundException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("gRPC VerifySku failed for {}, falling back to cache", sku, e);
            return fromCacheOrThrow(sku);
        }
    }

    private ProductSnapshot fromCacheOrThrow(String sku) {
        String json = redis.opsForValue().get(CACHE_KEY + sku);
        if (json == null) {
            throw new ProductUnavailableException(ErrorMessages.PRODUCT_UNAVAILABLE);
        }
        try {
            return objectMapper.readValue(json, ProductSnapshot.class);
        } catch (Exception e) {
            throw new ProductUnavailableException(ErrorMessages.PRODUCT_UNAVAILABLE);
        }
    }

    private void cache(String sku, ProductSnapshot snap) {
        try {
            redis.opsForValue().set(CACHE_KEY + sku, objectMapper.writeValueAsString(snap),
                    Duration.ofMinutes(cacheTtlMinutes));
        } catch (Exception e) {
            log.warn("Failed to cache product snapshot for {}", sku, e);
        }
    }
}
