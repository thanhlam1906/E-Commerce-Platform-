package com.example.productcatalogservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ProductResponse {
    private String id;
    private String name;
    private String slug;
    private String description;
    private String categoryId;
    private String brand;
    private boolean isActive;
    private List<Variant> variants;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    public static class Variant {
        private String sku;
        private String name;
        private BigDecimal price;
        private Map<String, String> attributes;
        private List<String> images;
    }
}
