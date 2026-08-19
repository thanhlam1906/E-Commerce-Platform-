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
public class OrderHistoryResponse {

    private UUID id;
    private UUID orderId;
    private String oldStatus;
    private String newStatus;
    private UUID changedBy;
    private String reason;
    private Instant createdAt;
}
