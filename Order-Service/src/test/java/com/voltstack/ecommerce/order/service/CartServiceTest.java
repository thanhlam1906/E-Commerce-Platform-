package com.voltstack.ecommerce.order.service;

import com.voltstack.ecommerce.order.client.ProductClient;
import com.voltstack.ecommerce.order.client.ProductSnapshot;
import com.voltstack.ecommerce.order.dto.request.AddCartItemRequest;
import com.voltstack.ecommerce.order.dto.response.CartItemResponse;
import com.voltstack.ecommerce.order.dto.response.CartResponse;
import com.voltstack.ecommerce.order.exception.SkuNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private StringRedisTemplate redis;
    private ProductClient productClient;
    private InventoryService inventoryService;
    private HashOperations<String, Object, Object> hashOps;
    private ValueOperations<String, String> valueOps;
    private CartService cartService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        productClient = mock(ProductClient.class);
        inventoryService = mock(InventoryService.class);
        hashOps = mock(HashOperations.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        cartService = new CartService(redis, productClient, inventoryService);
        ReflectionTestUtils.setField(cartService, "cartTtlDays", 7L);
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuth() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of()));
    }

    private ProductSnapshot snapshot() {
        return new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00");
    }

    // ---- resolveKey ----

    @Test
    void resolveKey_authenticatedUser_usesUserPrefix() {
        setAuth();
        assertEquals("cart:" + userId, cartService.resolveKey(null));
        assertEquals("cart:" + userId, cartService.resolveKey("ignored-session"));
    }

    @Test
    void resolveKey_guestWithSession_usesGuestPrefix() {
        SecurityContextHolder.clearContext();
        assertEquals("cart:guest:abc123", cartService.resolveKey("abc123"));
    }

    @Test
    void resolveKey_guestWithoutSession_throws() {
        SecurityContextHolder.clearContext();
        assertThrows(IllegalArgumentException.class, () -> cartService.resolveKey(null));
        assertThrows(IllegalArgumentException.class, () -> cartService.resolveKey("  "));
    }

    // ---- checkout lock (M2) ----

    @Test
    void tryAcquireCheckoutLock_returnsTokenOnAcquire() {
        when(valueOps.setIfAbsent(eq("cart:checkout:" + userId), anyString(), any(Duration.class))).thenReturn(true);

        String token = cartService.tryAcquireCheckoutLock(userId);

        assertNotNull(token);
        verify(valueOps).setIfAbsent(eq("cart:checkout:" + userId), eq(token), any(Duration.class));
    }

    @Test
    void tryAcquireCheckoutLock_lockBusy_returnsNull() {
        when(valueOps.setIfAbsent(eq("cart:checkout:" + userId), anyString(), any(Duration.class))).thenReturn(false);

        assertNull(cartService.tryAcquireCheckoutLock(userId));
    }

    @Test
    void releaseCheckoutLock_usesCompareAndDeleteScript() {
        String token = "tok-1";

        cartService.releaseCheckoutLock(userId, token);

        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of("cart:checkout:" + userId)), eq(token));
        verify(redis, never()).delete(anyString());
    }

    @Test
    void releaseCheckoutLock_nullToken_noOp() {
        cartService.releaseCheckoutLock(userId, null);

        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any());
        verify(redis, never()).delete(anyString());
    }

    // ---- addItem / readRawCart / clearCart / getCart ----

    @Test
    void addItem_incrementsHashAndRefreshesTtl() {
        setAuth();
        String key = "cart:" + userId;
        when(hashOps.entries(key)).thenReturn(Map.<Object, Object>of("SKU1", "2"));
        when(productClient.getSnapshot("SKU1")).thenReturn(snapshot());
        when(inventoryService.checkAvailable("SKU1")).thenReturn(5);

        CartResponse resp = cartService.addItem(null, AddCartItemRequest.builder().sku("SKU1").quantity(2).build());

        verify(hashOps).increment(key, "SKU1", 2);
        verify(redis, org.mockito.Mockito.times(2)).expire(eq(key), any(Duration.class));
        assertEquals(1, resp.getItemCount());
    }

    @Test
    void addItem_unknownSku_throwsSkuNotFound() {
        setAuth();
        when(productClient.getSnapshot("SKU-X")).thenThrow(new SkuNotFoundException("Sản phẩm không tồn tại: SKU-X"));

        assertThrows(SkuNotFoundException.class,
                () -> cartService.addItem(null, AddCartItemRequest.builder().sku("SKU-X").quantity(1).build()));
    }

    @Test
    void readRawCart_mapsEntriesToCartItems() {
        setAuth();
        Map<Object, Object> entries = new HashMap<>();
        entries.put("SKU1", "2");
        entries.put("SKU2", "5");
        when(hashOps.entries("cart:" + userId)).thenReturn(entries);

        List<CartService.CartItem> items = cartService.readRawCart(null);

        assertEquals(2, items.size());
        assertTrue(items.contains(new CartService.CartItem("SKU1", 2)));
        assertTrue(items.contains(new CartService.CartItem("SKU2", 5)));
    }

    @Test
    void readRawCart_guestWithoutSession_returnsEmpty() {
        SecurityContextHolder.clearContext();
        assertTrue(cartService.readRawCart(null).isEmpty());
    }

    @Test
    void clearCart_deletesKeyAndReturnsEmptyCart() {
        setAuth();
        CartResponse resp = cartService.clearCart(null);

        verify(redis).delete("cart:" + userId);
        assertEquals(0, resp.getItemCount());
        assertTrue(resp.getItems().isEmpty());
    }

    @Test
    void getCart_buildsItemWithStockWarningWhenAvailableTooLow() {
        setAuth();
        when(hashOps.entries("cart:" + userId)).thenReturn(Map.<Object, Object>of("SKU1", "2"));
        when(productClient.getSnapshot("SKU1")).thenReturn(snapshot());
        when(inventoryService.checkAvailable("SKU1")).thenReturn(1);

        CartResponse resp = cartService.getCart(null);

        CartItemResponse item = resp.getItems().get(0);
        assertTrue(item.getStockWarning());
        assertEquals(new BigDecimal("200.00"), item.getSubtotal());
        assertEquals(new BigDecimal("200.00"), resp.getTotalAmount());
    }

    @Test
    void getCart_noStockWarningWhenAvailableSufficient() {
        setAuth();
        when(hashOps.entries("cart:" + userId)).thenReturn(Map.<Object, Object>of("SKU1", "2"));
        when(productClient.getSnapshot("SKU1")).thenReturn(snapshot());
        when(inventoryService.checkAvailable("SKU1")).thenReturn(5);

        CartResponse resp = cartService.getCart(null);

        assertFalse(resp.getItems().get(0).getStockWarning());
    }

    @Test
    void getCart_missingProduct_fallsBackToWarningItem() {
        setAuth();
        when(hashOps.entries("cart:" + userId)).thenReturn(Map.<Object, Object>of("SKU1", "2"));
        when(productClient.getSnapshot("SKU1")).thenThrow(new SkuNotFoundException("Sản phẩm không tồn tại: SKU1"));

        CartResponse resp = cartService.getCart(null);

        CartItemResponse item = resp.getItems().get(0);
        assertTrue(item.getStockWarning());
        assertEquals(BigDecimal.ZERO, item.getSubtotal());
    }

    @Test
    void getCart_guestWithoutSession_returnsEmpty() {
        SecurityContextHolder.clearContext();
        CartResponse resp = cartService.getCart(null);

        assertEquals(0, resp.getItemCount());
        assertTrue(resp.getItems().isEmpty());
        verify(redis, never()).opsForHash();
    }
}
