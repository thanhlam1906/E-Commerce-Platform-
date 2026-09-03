package com.voltstack.ecommerce.order.controller;

import com.voltstack.ecommerce.order.dto.request.CreateOrderRequest;
import com.voltstack.ecommerce.order.dto.request.UpdateOrderStatusRequest;
import com.voltstack.ecommerce.order.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.order.dto.response.CheckoutResponse;
import com.voltstack.ecommerce.order.dto.response.OrderHistoryResponse;
import com.voltstack.ecommerce.order.dto.response.OrderResponse;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.service.InventoryService;
import com.voltstack.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    @PostMapping
    public ApiDataResponse<CheckoutResponse> createOrder(@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                         @Valid @RequestBody CreateOrderRequest req) {
        return ApiDataResponse.ok(orderService.createOrder(req, idempotencyKey));
    }

    @GetMapping
    public ApiDataResponse<Page<OrderResponse>> listMyOrders(@RequestParam(required = false) String status,
                                                             @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiDataResponse.ok(orderService.listMyOrders(parseStatus(status), pageable));
    }

    /** Số lượng đã bán theo SKU (public, không cần auth) — literal path thắng /{id}. */
    @GetMapping("/sold")
    public ApiDataResponse<Map<String, Integer>> getSoldCounts(@RequestParam("skus") List<String> skus) {
        return ApiDataResponse.ok(inventoryService.getSoldCounts(skus));
    }

    @GetMapping("/{id}")
    public ApiDataResponse<OrderResponse> getOrder(@PathVariable UUID id) {
        return ApiDataResponse.ok(orderService.getOrder(id));
    }

    @PostMapping("/{id}/cancel")
    public ApiDataResponse<OrderResponse> cancelOrder(@PathVariable UUID id) {
        return ApiDataResponse.ok(orderService.cancelOrder(id));
    }

    @GetMapping("/{id}/history")
    public ApiDataResponse<List<OrderHistoryResponse>> getHistory(@PathVariable UUID id) {
        return ApiDataResponse.ok(orderService.getHistory(id));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ORDER_ADMIN','SUPER_ADMIN')")
    public ApiDataResponse<Page<OrderResponse>> listAllOrders(@RequestParam(required = false) String status,
                                                              @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiDataResponse.ok(orderService.listAllOrders(parseStatus(status), pageable));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ORDER_ADMIN','SUPER_ADMIN')")
    public ApiDataResponse<OrderResponse> updateStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateOrderStatusRequest req) {
        return ApiDataResponse.ok(orderService.adminUpdateStatus(id, parseStatus(req.getStatus()), req.getReason()));
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
        }
    }
}
