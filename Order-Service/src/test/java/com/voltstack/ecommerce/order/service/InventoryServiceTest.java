package com.voltstack.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.order.dto.request.ImportInventoryRequest;
import com.voltstack.ecommerce.order.dto.response.ImportInventoryResponse;
import com.voltstack.ecommerce.order.entity.Inventory;
import com.voltstack.ecommerce.order.entity.InventoryTransaction;
import com.voltstack.ecommerce.order.entity.InventoryTxnType;
import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.repository.InventoryRepository;
import com.voltstack.ecommerce.order.repository.InventoryTransactionRepository;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private InventoryRepository inventoryRepository;
    private InventoryTransactionRepository transactionRepository;
    private OutboxRepository outboxRepository;
    private ObjectMapper objectMapper;
    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryRepository = mock(InventoryRepository.class);
        transactionRepository = mock(InventoryTransactionRepository.class);
        outboxRepository = mock(OutboxRepository.class);
        objectMapper = mock(ObjectMapper.class);
        inventoryService = new InventoryService(inventoryRepository, transactionRepository, outboxRepository, objectMapper);
        ReflectionTestUtils.setField(inventoryService, "lowStockThreshold", 5);
    }

    private Inventory inv(int quantity, int reserved) {
        return Inventory.builder().sku("SKU1").quantity(quantity).reserved(reserved).updatedAt(Instant.now()).build();
    }

    // ---- importStock ----

    @Test
    void importStock_noExistingRow_savesNewInventory() {
        ImportInventoryRequest req = ImportInventoryRequest.builder().sku("SKU1").quantity(10).build();
        when(inventoryRepository.increaseQuantity("SKU1", 10)).thenReturn(0);
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(inv(10, 0)));

        ImportInventoryResponse resp = inventoryService.importStock(req);

        verify(inventoryRepository).save(any(Inventory.class));
        verify(transactionRepository).save(any(InventoryTransaction.class));
        assertEquals(10, resp.getQuantity());
        assertTrue(resp.getReference().startsWith("import_batch_"));
    }

    @Test
    void importStock_existingRow_skipsSave() {
        ImportInventoryRequest req = ImportInventoryRequest.builder().sku("SKU1").quantity(10).reference("batch-1").build();
        when(inventoryRepository.increaseQuantity("SKU1", 10)).thenReturn(1);
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(inv(20, 0)));

        inventoryService.importStock(req);

        verify(inventoryRepository, never()).save(any(Inventory.class));
        verify(transactionRepository).save(argThatType(InventoryTxnType.IMPORT));
    }

    // ---- reserve ----

    @Test
    void reserve_success_savesTransactionAndReturnsTrue() {
        when(inventoryRepository.reserve("SKU1", 2)).thenReturn(1);
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(inv(10, 2)));

        assertTrue(inventoryService.reserve("SKU1", 2, "order:abc"));
        verify(transactionRepository).save(argThatType(InventoryTxnType.RESERVE));
    }

    @Test
    void reserve_failure_returnsFalseWithoutSaving() {
        when(inventoryRepository.reserve("SKU1", 2)).thenReturn(0);

        assertFalse(inventoryService.reserve("SKU1", 2, "order:abc"));
        verify(transactionRepository, never()).save(any(InventoryTransaction.class));
    }

    @Test
    void reserve_emitsLowStockEventWhenBelowThresholdAndNotNotified() throws Exception {
        when(inventoryRepository.reserve("SKU1", 9)).thenReturn(1);
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(inv(10, 9)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        assertTrue(inventoryService.reserve("SKU1", 9, "ref"));

        verify(outboxRepository).save(argThat(e -> "InventoryLowEvent".equals(e.getEventType()) && !e.getPublished()));
        verify(inventoryRepository).save(argThat(i -> Boolean.TRUE.equals(i.getLowStockNotified())));
    }

    // ---- release / deductReserved ----

    @Test
    void release_delegatesAndSavesTransaction() {
        inventoryService.release("SKU1", 2, "ref");

        verify(inventoryRepository).release("SKU1", 2);
        verify(transactionRepository).save(argThatType(InventoryTxnType.RELEASE));
    }

    @Test
    void deductReserved_delegatesAndSavesTransaction() {
        inventoryService.deductReserved("SKU1", 2, "ref");

        verify(inventoryRepository).deduct("SKU1", 2);
        verify(transactionRepository).save(argThatType(InventoryTxnType.DEDUCT));
    }

    // ---- checkAvailable ----

    @Test
    void checkAvailable_quantityMinusReserved() {
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(inv(10, 3)));
        assertEquals(7, inventoryService.checkAvailable("SKU1"));
    }

    @Test
    void checkAvailable_missingSku_returnsZero() {
        assertEquals(0, inventoryService.checkAvailable("MISSING"));
    }

    // ---- emitLowStockIfNeeded dedup ----

    @Test
    void lowStockEvent_notEmittedWhenAlreadyNotified() {
        ImportInventoryRequest req = ImportInventoryRequest.builder().sku("SKU1").quantity(1).build();
        when(inventoryRepository.increaseQuantity("SKU1", 1)).thenReturn(1);
        Inventory alreadyNotified = Inventory.builder().sku("SKU1").quantity(2).reserved(0)
                .lowStockNotified(true).updatedAt(Instant.now()).build();
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(alreadyNotified));

        inventoryService.importStock(req);

        verify(outboxRepository, never()).save(any(OutboxEvent.class));
        verify(inventoryRepository, never()).save(argThat(i -> Boolean.TRUE.equals(i.getLowStockNotified())));
    }

    @Test
    void lowStockEvent_emittedWhenBelowThresholdAndNotNotified() throws Exception {
        ImportInventoryRequest req = ImportInventoryRequest.builder().sku("SKU1").quantity(1).build();
        when(inventoryRepository.increaseQuantity("SKU1", 1)).thenReturn(1);
        when(inventoryRepository.findBySku("SKU1")).thenReturn(Optional.of(inv(2, 0)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        inventoryService.importStock(req);

        verify(outboxRepository).save(argThat(e -> "InventoryLowEvent".equals(e.getEventType()) && !e.getPublished()));
    }

    private static InventoryTransaction argThatType(InventoryTxnType type) {
        return org.mockito.ArgumentMatchers.argThat(t -> t.getType() == type);
    }
}
