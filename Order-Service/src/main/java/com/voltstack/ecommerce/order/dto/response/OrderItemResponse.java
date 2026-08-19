package com.voltstack.ecommerce.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private String sku;
    private String productName;
    private String variantName;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}
