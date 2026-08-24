package com.voltstack.ecommerce.order.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.grpc.ProductVerifyServiceGrpc.ProductVerifyServiceBlockingStub;
import com.voltstack.ecommerce.grpc.SkuRequest;
import com.voltstack.ecommerce.grpc.VerifySkuResponse;
import com.voltstack.ecommerce.order.exception.ProductUnavailableException;
import com.voltstack.ecommerce.order.exception.SkuNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductClientTest {

    private ProductVerifyServiceBlockingStub stub;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private ProductClient productClient;

    private static final com.voltstack.ecommerce.grpc.ProductSnapshot GRPC_SNAP =
            com.voltstack.ecommerce.grpc.ProductSnapshot.newBuilder()
                    .setSku("SKU1").setProductName("T-Shirt").setVariantName("Black/M").setPrice("100.00")
                    .build();

    @BeforeEach
    void setUp() {
        stub = mock(ProductVerifyServiceBlockingStub.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        objectMapper = mock(ObjectMapper.class);
        productClient = new ProductClient(redis, objectMapper);
        ReflectionTestUtils.setField(productClient, "productStub", stub);
        ReflectionTestUtils.setField(productClient, "cacheTtlMinutes", 60L);
        // M3: every call goes through withDeadlineAfter, so the fluent stub must return itself.
        when(stub.withDeadlineAfter(anyLong(), any())).thenReturn(stub);
    }

    @Test
    void getSnapshot_grpcSuccess_returnsSnapshotAndCaches() throws Exception {
        VerifySkuResponse resp = VerifySkuResponse.newBuilder().setExists(true).setSnapshot(GRPC_SNAP).build();
        when(stub.verifySku(any(SkuRequest.class))).thenReturn(resp);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        ProductSnapshot snap = productClient.getSnapshot("SKU1");

        assertEquals("SKU1", snap.sku());
        assertEquals("T-Shirt", snap.productName());
        assertEquals(new java.math.BigDecimal("100.00"), snap.unitPrice());
        verify(valueOps).set(eq("product:snapshot:SKU1"), eq("{}"), any(Duration.class));
    }

    @Test
    void getSnapshot_setsGrpcDeadline() {
        VerifySkuResponse resp = VerifySkuResponse.newBuilder().setExists(true).setSnapshot(GRPC_SNAP).build();
        when(stub.verifySku(any(SkuRequest.class))).thenReturn(resp);

        productClient.getSnapshot("SKU1");

        verify(stub).withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    @Test
    void getSnapshot_grpcSaysNotExists_throwsSkuNotFound() {
        VerifySkuResponse resp = VerifySkuResponse.newBuilder().setExists(false).build();
        when(stub.verifySku(any(SkuRequest.class))).thenReturn(resp);

        assertThrows(SkuNotFoundException.class, () -> productClient.getSnapshot("SKU1"));
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void getSnapshot_grpcFails_cacheHit_returnsCachedSnapshot() throws Exception {
        when(stub.verifySku(any(SkuRequest.class))).thenThrow(new RuntimeException("grpc down"));
        when(valueOps.get("product:snapshot:SKU1")).thenReturn("cached-json");
        ProductSnapshot cached = new ProductSnapshot("SKU1", "Old", "Old/V", "50.00");
        when(objectMapper.readValue("cached-json", ProductSnapshot.class)).thenReturn(cached);

        ProductSnapshot snap = productClient.getSnapshot("SKU1");

        assertEquals("Old", snap.productName());
        assertEquals(new java.math.BigDecimal("50.00"), snap.unitPrice());
    }

    @Test
    void getSnapshot_grpcFails_cacheMiss_throwsProductUnavailable() {
        when(stub.verifySku(any(SkuRequest.class))).thenThrow(new RuntimeException("grpc down"));
        when(valueOps.get("product:snapshot:SKU1")).thenReturn(null);

        assertThrows(ProductUnavailableException.class, () -> productClient.getSnapshot("SKU1"));
    }

    @Test
    void getSnapshot_grpcFails_corruptCache_throwsProductUnavailable() throws Exception {
        when(stub.verifySku(any(SkuRequest.class))).thenThrow(new RuntimeException("grpc down"));
        when(valueOps.get("product:snapshot:SKU1")).thenReturn("not-json");
        when(objectMapper.readValue("not-json", ProductSnapshot.class)).thenThrow(new RuntimeException("parse error"));

        assertThrows(ProductUnavailableException.class, () -> productClient.getSnapshot("SKU1"));
    }
}
