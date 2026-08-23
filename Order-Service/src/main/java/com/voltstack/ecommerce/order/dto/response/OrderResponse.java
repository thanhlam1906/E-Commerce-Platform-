package com.voltstack.ecommerce.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;
    private UUID userId;
    private String orderNumber;
    private String status;
    private BigDecimal totalAmount;
    private String currency;
    private String shippingAddressSnapshot;
    private String paymentMethod;
    private String paymentUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderItemResponse> items;
}
