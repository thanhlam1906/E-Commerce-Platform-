package com.voltstack.ecommerce.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {

    private String sku;
    private Integer quantity;
    private Integer reserved;
    private Integer available;
    private Instant updatedAt;
}
