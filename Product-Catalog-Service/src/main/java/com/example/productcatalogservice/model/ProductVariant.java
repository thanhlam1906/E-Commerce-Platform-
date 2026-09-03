package com.example.productcatalogservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
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
    /** Giá khuyến mãi (null = không sale). Sale áp dụng khi salePrice+saleEndTime có và còn hạn. */
    private BigDecimal salePrice;
    private Instant saleEndTime;
    @Builder.Default
    private Map<String, String> attributes = new HashMap<>();
    @Builder.Default
    private List<String> images = new ArrayList<>();

    public boolean isOnSale(Instant now) {
        return salePrice != null && saleEndTime != null
                && saleEndTime.isAfter(now)
                && salePrice.compareTo(price) < 0;
    }

    /** Giá tính tiền: sale nếu đang sale, ngược lại giá gốc. */
    public BigDecimal effectivePrice(Instant now) {
        return isOnSale(now) ? salePrice : price;
    }
}
