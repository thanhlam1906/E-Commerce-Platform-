package com.example.productcatalogservice.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductVariantTest {

    private final Instant now = Instant.parse("2026-09-03T12:00:00Z");

    private ProductVariant variant(BigDecimal price, BigDecimal salePrice, Instant saleEndTime) {
        return ProductVariant.builder()
                .sku("SKU1")
                .name("v")
                .price(price)
                .salePrice(salePrice)
                .saleEndTime(saleEndTime)
                .build();
    }

    @Test
    void isOnSale_activeSale_effectiveIsSalePrice() {
        ProductVariant v = variant(new BigDecimal("1000000"), new BigDecimal("800000"), now.plusSeconds(3600));

        assertTrue(v.isOnSale(now));
        assertEquals(0, v.effectivePrice(now).compareTo(new BigDecimal("800000")));
    }

    @Test
    void isOnSale_expired_effectiveIsBasePrice() {
        ProductVariant v = variant(new BigDecimal("1000000"), new BigDecimal("800000"), now.minusSeconds(1));

        assertFalse(v.isOnSale(now));
        assertEquals(0, v.effectivePrice(now).compareTo(new BigDecimal("1000000")));
    }

    @Test
    void isOnSale_salePriceNotBelowPrice_false() {
        ProductVariant v = variant(new BigDecimal("1000000"), new BigDecimal("1000000"), now.plusSeconds(3600));

        assertFalse(v.isOnSale(now));
        assertEquals(0, v.effectivePrice(now).compareTo(new BigDecimal("1000000")));
    }

    @Test
    void isOnSale_missingSaleFields_false() {
        assertFalse(variant(new BigDecimal("1000000"), null, now.plusSeconds(3600)).isOnSale(now));
        assertFalse(variant(new BigDecimal("1000000"), new BigDecimal("800000"), null).isOnSale(now));
        assertEquals(0, variant(new BigDecimal("1000000"), null, null)
                .effectivePrice(now).compareTo(new BigDecimal("1000000")));
    }
}
