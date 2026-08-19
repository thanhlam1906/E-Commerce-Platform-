package com.voltstack.ecommerce.order.service;

import com.voltstack.ecommerce.order.client.ProductClient;
import com.voltstack.ecommerce.order.client.ProductSnapshot;
import com.voltstack.ecommerce.order.dto.request.AddCartItemRequest;
import com.voltstack.ecommerce.order.dto.request.UpdateCartItemRequest;
import com.voltstack.ecommerce.order.dto.response.CartItemResponse;
import com.voltstack.ecommerce.order.dto.response.CartResponse;
import com.voltstack.ecommerce.order.exception.ProductUnavailableException;
import com.voltstack.ecommerce.order.exception.SkuNotFoundException;
import com.voltstack.ecommerce.order.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private static final String PREFIX_USER = "cart:";
    private static final String PREFIX_GUEST = "cart:guest:";
    private static final String PREFIX_CHECKOUT = "cart:checkout:";
    private static final Duration CHECKOUT_LOCK_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redis;
    private final ProductClient productClient;
    private final InventoryService inventoryService;

    @Value("${order.cart-ttl-days:7}")
    private long cartTtlDays;

    private Duration ttl() {
        return Duration.ofDays(cartTtlDays);
    }

    public CartResponse getCart(String sessionId) {
        String key;
        try {
            key = resolveKey(sessionId);
        } catch (IllegalArgumentException e) {
            return emptyCart();
        }
        refreshTtl(key);
        List<CartItemResponse> items = new ArrayList<>();
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String sku = entry.getKey().toString();
            int quantity = Integer.parseInt(entry.getValue().toString());
            items.add(buildItem(sku, quantity));
        }
        return assemble(items);
    }

    public CartResponse addItem(String sessionId, AddCartItemRequest req) {
        String key = resolveKey(sessionId);
        redis.opsForHash().increment(key, req.getSku(), req.getQuantity());
        refreshTtl(key);
        return getCart(sessionId);
    }

    public CartResponse updateItem(String sessionId, String sku, UpdateCartItemRequest req) {
        String key = resolveKey(sessionId);
        redis.opsForHash().put(key, sku, String.valueOf(req.getQuantity()));
        refreshTtl(key);
        return getCart(sessionId);
    }

    public CartResponse removeItem(String sessionId, String sku) {
        String key = resolveKey(sessionId);
        redis.opsForHash().delete(key, sku);
        refreshTtl(key);
        return getCart(sessionId);
    }

    public CartResponse clearCart(String sessionId) {
        String key = resolveKey(sessionId);
        redis.delete(key);
        return emptyCart();
    }

    /**
     * Serialize checkout per user so two concurrent POST /orders cannot both read the same
     * cart, double-reserve stock and double-charge. TTL guards against a crash mid-checkout.
     */
    public boolean tryAcquireCheckoutLock(UUID userId) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(PREFIX_CHECKOUT + userId, "1", CHECKOUT_LOCK_TTL));
    }

    public void releaseCheckoutLock(UUID userId) {
        redis.delete(PREFIX_CHECKOUT + userId);
    }

    /** Remove only the ordered SKUs after checkout — items added mid-payment survive. */
    public CartResponse removeItems(String sessionId, Collection<String> skus) {
        String key = resolveKey(sessionId);
        redis.opsForHash().delete(key, skus.toArray());
        refreshTtl(key);
        return getCart(sessionId);
    }

    /** Raw sku→quantity entries, used by checkout to reserve stock. */
    public List<CartItem> readRawCart(String sessionId) {
        try {
            String key = resolveKey(sessionId);
            Map<Object, Object> entries = redis.opsForHash().entries(key);
            return entries.entrySet().stream()
                    .map(e -> new CartItem(e.getKey().toString(), Integer.parseInt(e.getValue().toString())))
                    .toList();
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    public CartResponse mergeCart(String sessionId) {
        UUID userId = SecurityUtils.requireUserId();
        String guestKey = PREFIX_GUEST + sessionId;
        String userKey = PREFIX_USER + userId;
        Map<Object, Object> session = redis.opsForHash().entries(guestKey);
        for (Map.Entry<Object, Object> entry : session.entrySet()) {
            redis.opsForHash().increment(userKey, entry.getKey().toString(), Long.parseLong(entry.getValue().toString()));
        }
        redis.delete(guestKey);
        refreshTtl(userKey);
        return getCart(null);
    }

    /** All Redis Hash operations go through here so TTL is refreshed on every cart touch. */
    public void refreshTtl(String key) {
        redis.expire(key, ttl());
    }

    public String resolveKey(String sessionId) {
        UUID userId = SecurityUtils.currentUserId();
        if (userId != null) {
            return PREFIX_USER + userId;
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Thiếu X-Session-Id");
        }
        return PREFIX_GUEST + sessionId;
    }

    private CartItemResponse buildItem(String sku, int quantity) {
        try {
            ProductSnapshot snap = productClient.getSnapshot(sku);
            BigDecimal unitPrice = snap.unitPrice();
            int available = inventoryService.checkAvailable(sku);
            return CartItemResponse.builder()
                    .sku(sku).productName(snap.productName()).variantName(snap.variantName())
                    .unitPrice(unitPrice).quantity(quantity)
                    .subtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                    .stockWarning(available < quantity)
                    .build();
        } catch (SkuNotFoundException | ProductUnavailableException e) {
            return CartItemResponse.builder()
                    .sku(sku).quantity(quantity).subtotal(BigDecimal.ZERO).stockWarning(true)
                    .build();
        }
    }

    private CartResponse assemble(List<CartItemResponse> items) {
        BigDecimal total = items.stream()
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder().items(items).totalAmount(total).itemCount(items.size()).build();
    }

    private CartResponse emptyCart() {
        return CartResponse.builder().items(List.of()).totalAmount(BigDecimal.ZERO).itemCount(0).build();
    }

    public record CartItem(String sku, int quantity) {}
}
