package com.voltstack.ecommerce.order.client;

import java.math.BigDecimal;

/**
 * Internal product snapshot (denormalized price as String per proto, surfaced as BigDecimal
 * at the service boundary). Stored in Redis as JSON for the gRPC fallback cache.
 */
public record ProductSnapshot(String sku, String productName, String variantName, String price) {

    public BigDecimal unitPrice() {
        return new BigDecimal(price);
    }

    public static ProductSnapshot fromGrpc(com.voltstack.ecommerce.grpc.ProductSnapshot g) {
        return new ProductSnapshot(g.getSku(), g.getProductName(), g.getVariantName(), g.getPrice());
    }
}
