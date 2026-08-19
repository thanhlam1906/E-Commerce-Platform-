package com.voltstack.ecommerce.order.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionResponse {

    private UUID id;
    private String sku;
    private String type;
    private Integer quantity;
    private String reference;
    private Instant createdAt;
}
