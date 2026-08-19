package com.voltstack.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.order.constant.ErrorMessages;
import com.voltstack.ecommerce.order.dto.request.ImportInventoryRequest;
import com.voltstack.ecommerce.order.dto.response.ImportInventoryResponse;
import com.voltstack.ecommerce.order.dto.response.InventoryTransactionResponse;
import com.voltstack.ecommerce.order.dto.response.StockResponse;
import com.voltstack.ecommerce.order.entity.Inventory;
import com.voltstack.ecommerce.order.entity.InventoryTransaction;
import com.voltstack.ecommerce.order.entity.InventoryTxnType;
import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.order.repository.InventoryRepository;
import com.voltstack.ecommerce.order.repository.InventoryTransactionRepository;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Value("${inventory.low-stock-threshold:5}")
    private int lowStockThreshold;

    @Transactional
    public ImportInventoryResponse importStock(ImportInventoryRequest req) {
        String reference = req.getReference() == null || req.getReference().isBlank()
                ? "import_batch_" + UUID.randomUUID()
                : req.getReference();
        int rows = inventoryRepository.increaseQuantity(req.getSku(), req.getQuantity());
        if (rows == 0) {
            inventoryRepository.save(Inventory.builder()
                    .sku(req.getSku()).quantity(req.getQuantity()).reserved(0).version(0L).updatedAt(Instant.now())
                    .build());
        }
        transactionRepository.save(InventoryTransaction.builder()
                .sku(req.getSku()).type(InventoryTxnType.IMPORT).quantity(req.getQuantity()).reference(reference).build());
        emitLowStockIfNeeded(req.getSku());
        Inventory inv = inventoryRepository.findBySku(req.getSku()).orElseThrow();
        return ImportInventoryResponse.builder()
                .sku(inv.getSku()).quantity(inv.getQuantity()).reference(reference).updatedAt(inv.getUpdatedAt()).build();
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(String sku) {
        Inventory inv = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.INVENTORY_NOT_FOUND));
        return toStockResponse(inv);
    }

    @Transactional(readOnly = true)
    public Page<StockResponse> listStock(Pageable pageable) {
        return inventoryRepository.findAll(pageable).map(this::toStockResponse);
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionResponse> getTransactions(String sku) {
        return transactionRepository.findBySkuOrderByCreatedAtDesc(sku).stream()
                .map(t -> InventoryTransactionResponse.builder()
                        .id(t.getId()).sku(t.getSku()).type(t.getType().name())
                        .quantity(t.getQuantity()).reference(t.getReference()).createdAt(t.getCreatedAt())
                        .build())
                .toList();
    }

    public int checkAvailable(String sku) {
        return inventoryRepository.findBySku(sku)
                .map(inv -> inv.getQuantity() - inv.getReserved())
                .orElse(0);
    }

    @Transactional
    public boolean reserve(String sku, int quantity, String reference) {
        if (inventoryRepository.reserve(sku, quantity) == 1) {
            transactionRepository.save(InventoryTransaction.builder()
                    .sku(sku).type(InventoryTxnType.RESERVE).quantity(quantity).reference(reference).build());
            emitLowStockIfNeeded(sku);
            return true;
        }
        return false;
    }

    @Transactional
    public void release(String sku, int quantity, String reference) {
        inventoryRepository.release(sku, quantity);
        transactionRepository.save(InventoryTransaction.builder()
                .sku(sku).type(InventoryTxnType.RELEASE).quantity(quantity).reference(reference).build());
        emitLowStockIfNeeded(sku);
    }

    @Transactional
    public void deductReserved(String sku, int quantity, String reference) {
        inventoryRepository.deduct(sku, quantity);
        transactionRepository.save(InventoryTransaction.builder()
                .sku(sku).type(InventoryTxnType.DEDUCT).quantity(quantity).reference(reference).build());
        emitLowStockIfNeeded(sku);
    }

    private StockResponse toStockResponse(Inventory inv) {
        return StockResponse.builder()
                .sku(inv.getSku()).quantity(inv.getQuantity()).reserved(inv.getReserved())
                .available(inv.getQuantity() - inv.getReserved()).updatedAt(inv.getUpdatedAt())
                .build();
    }

    /** Emit InventoryLowEvent at most once per SKU while it stays below threshold.
     *  The flag resets once stock returns above threshold, so a fresh dip re-notifies (M5). */
    private void emitLowStockIfNeeded(String sku) {
        Inventory inv = inventoryRepository.findBySku(sku).orElse(null);
        if (inv == null) {
            return;
        }
        int available = inv.getQuantity() - inv.getReserved();
        if (available >= lowStockThreshold) {
            if (Boolean.TRUE.equals(inv.getLowStockNotified())) {
                inv.setLowStockNotified(false);
                inventoryRepository.save(inv);
            }
            return;
        }
        if (Boolean.TRUE.equals(inv.getLowStockNotified())) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "sku", sku, "available", available, "threshold", lowStockThreshold));
            outboxRepository.save(OutboxEvent.builder()
                    .eventType("InventoryLowEvent").aggregateId(sku).payload(payload).published(false).build());
            inv.setLowStockNotified(true);
            inventoryRepository.save(inv);
        } catch (Exception e) {
            log.warn("Failed to build InventoryLowEvent for {}", sku, e);
        }
    }
}
