package com.example.productcatalogservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {
    private String sku;
    private String name;
    @Builder.Default
    private BigDecimal price = BigDecimal.ZERO;
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();
    @Builder.Default
    private List<String> images = new ArrayList<>();
    private Integer stockQuantity;
}
