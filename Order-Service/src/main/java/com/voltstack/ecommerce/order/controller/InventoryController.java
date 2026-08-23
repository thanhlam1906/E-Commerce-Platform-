package com.voltstack.ecommerce.order.controller;

import com.voltstack.ecommerce.order.dto.request.ImportInventoryRequest;
import com.voltstack.ecommerce.order.dto.response.ApiDataResponse;
import com.voltstack.ecommerce.order.dto.response.ImportInventoryResponse;
import com.voltstack.ecommerce.order.dto.response.InventoryTransactionResponse;
import com.voltstack.ecommerce.order.dto.response.StockResponse;
import com.voltstack.ecommerce.order.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ORDER_ADMIN','SUPER_ADMIN')")
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/import")
    public ApiDataResponse<ImportInventoryResponse> importStock(@Valid @RequestBody ImportInventoryRequest req) {
        return ApiDataResponse.ok(inventoryService.importStock(req));
    }

    @GetMapping("/{sku}")
    public ApiDataResponse<StockResponse> getStock(@PathVariable String sku) {
        return ApiDataResponse.ok(inventoryService.getStock(sku));
    }

    @GetMapping
    public ApiDataResponse<Page<StockResponse>> listStock(Pageable pageable) {
        return ApiDataResponse.ok(inventoryService.listStock(pageable));
    }

    @GetMapping("/{sku}/transactions")
    public ApiDataResponse<List<InventoryTransactionResponse>> getTransactions(@PathVariable String sku) {
        return ApiDataResponse.ok(inventoryService.getTransactions(sku));
    }
}
