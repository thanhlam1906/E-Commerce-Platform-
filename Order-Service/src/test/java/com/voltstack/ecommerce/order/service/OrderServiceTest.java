package com.voltstack.ecommerce.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.order.client.PaymentClient;
import com.voltstack.ecommerce.order.client.ProductClient;
import com.voltstack.ecommerce.order.client.ProductSnapshot;
import com.voltstack.ecommerce.order.dto.request.CreateOrderRequest;
import com.voltstack.ecommerce.order.dto.response.CheckoutResponse;
import com.voltstack.ecommerce.order.dto.response.OrderResponse;
import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderItem;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.exception.DuplicateResourceException;
import com.voltstack.ecommerce.order.exception.InvalidOrderStateException;
import com.voltstack.ecommerce.order.exception.PaymentUnavailableException;
import com.voltstack.ecommerce.order.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.order.repository.OrderItemRepository;
import com.voltstack.ecommerce.order.repository.OrderRepository;
import com.voltstack.ecommerce.order.repository.OrderStatusHistoryRepository;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private OrderStatusHistoryRepository historyRepository;
    private OutboxRepository outboxRepository;
    private InventoryService inventoryService;
    private CartService cartService;
    private ProductClient productClient;
    private PaymentClient paymentClient;
    private OrderNumberGenerator orderNumberGenerator;
    private ObjectMapper objectMapper;
    private OrderService orderService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        historyRepository = mock(OrderStatusHistoryRepository.class);
        outboxRepository = mock(OutboxRepository.class);
        inventoryService = mock(InventoryService.class);
        cartService = mock(CartService.class);
        productClient = mock(ProductClient.class);
        paymentClient = mock(PaymentClient.class);
        orderNumberGenerator = mock(OrderNumberGenerator.class);
        objectMapper = mock(ObjectMapper.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        orderService = new OrderService(orderRepository, orderItemRepository, historyRepository, outboxRepository,
                inventoryService, cartService, productClient, paymentClient, orderNumberGenerator, objectMapper, txManager);
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

    private CreateOrderRequest orderRequest() {
        return CreateOrderRequest.builder().shippingAddress("123 Main St").paymentMethod("COD")
                .email("user@example.com").build();
    }

    private CreateOrderRequest onlineRequest() {
        return CreateOrderRequest.builder().shippingAddress("123 Main St").paymentMethod("VNPAY_QR").build();
    }

    private OrderItem item(String sku, int qty) {
        return OrderItem.builder().sku(sku).productName("T-Shirt").variantName("Black/M")
                .unitPrice(new BigDecimal("100.00")).quantity(qty).subtotal(new BigDecimal("200.00")).build();
    }

    /** Stub findById to return the mutable order, and transition/cancel to mutate it. */
    private AtomicReference<Order> orderRef(UUID orderId, OrderStatus status) {
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(status)
                .totalAmount(new BigDecimal("200.00")).currency("VND").paymentTransactionId(UUID.randomUUID())
                .shippingAddressSnapshot("addr").paymentMethod("COD").build();
        AtomicReference<Order> ref = new AtomicReference<>(order);
        when(orderRepository.findById(orderId)).thenAnswer(inv -> Optional.of(ref.get()));
        when(orderRepository.transition(eq(orderId), anyString(), anyString()))
                .thenAnswer(inv -> {
                    ref.get().setStatus(OrderStatus.valueOf(inv.getArgument(2)));
                    return 1;
                });
        when(orderRepository.cancelIfActive(orderId))
                .thenAnswer(inv -> {
                    ref.get().setStatus(OrderStatus.CANCELLED);
                    return 1;
                });
        return ref;
    }

    // ---- createOrder ----

    @Test
    void createOrder_unauthenticated_throws() {
        SecurityContextHolder.clearContext();
        assertThrows(ResourceNotFoundException.class, () -> orderService.createOrder(orderRequest(), "key-12345678"));
    }

    @Test
    void createOrder_invalidIdempotencyKey_throws() {
        setAuth();
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(orderRequest(), "short"));
    }

    @Test
    void createOrder_emptyCart_throws() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(orderRequest(), "key-12345678"));
        verify(cartService).releaseCheckoutLock(any(), any());
    }

    @Test
    void createOrder_checkoutLockBusy_throws() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> orderService.createOrder(orderRequest(), "key-12345678"));
        verify(cartService, never()).readRawCart(any());
    }

    @Test
    void createOrder_existingIdempotencyKey_returnsExistingOrder() {
        setAuth();
        Order existing = Order.builder().id(UUID.randomUUID()).userId(userId).orderNumber("OR-EXISTING")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("50.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD").build();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.of(existing));

        CheckoutResponse resp = orderService.createOrder(orderRequest(), "key-12345678");

        assertEquals(existing.getId(), resp.getOrderId());
        assertEquals("OR-EXISTING", resp.getOrderNumber());
        verify(cartService, never()).readRawCart(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void createOrder_happyPath_reservesSavesPaysAndRemovesOrderedSkus() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));
        when(productClient.getSnapshot("SKU1"))
                .thenReturn(new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00"));
        when(orderNumberGenerator.generate(any())).thenReturn("OR-20260819-00001");
        when(inventoryService.reserve(anyString(), anyInt(), anyString())).thenReturn(true);

        AtomicReference<Order> saved = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID()); // Hibernate assigns the id at persist
            saved.set(o);
            return o;
        });
        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(saved.get()));

        when(paymentClient.createPayment(any(), any(), any(), eq("VND"), any(), any(), any()))
                .thenReturn(new PaymentClient.PaymentResult(UUID.randomUUID(), "http://pay/vnpay", Instant.now().plusSeconds(300), null));

        CheckoutResponse resp = orderService.createOrder(onlineRequest(), "key-12345678");

        assertEquals("PENDING", resp.getStatus());
        assertEquals("OR-20260819-00001", resp.getOrderNumber());
        assertEquals(0, saved.get().getTotalAmount().compareTo(new BigDecimal("200.00")));
        // The id Hibernate assigned at persist is what the response, history and outbox must all carry.
        assertNotNull(resp.getOrderId());
        assertEquals(saved.get().getId(), resp.getOrderId());
        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.PENDING));
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.PENDING
                && saved.get().getId().equals(h.getOrderId())));
        verify(outboxRepository).save(argThat(e -> "OrderCreatedEvent".equals(e.getEventType()) && !e.getPublished()
                && saved.get().getId().toString().equals(e.getAggregateId())));
        // Inventory reservations are referenced by the persisted order id so compensation can release them.
        verify(inventoryService).reserve("SKU1", 2, "order:" + saved.get().getId());
        verify(paymentClient).createPayment(any(), any(), any(), eq("VND"), any(), any(), any());
        verify(orderRepository).updatePaymentInfo(any(), eq("http://pay/vnpay"), any());
        verify(cartService).removeItems(eq(null), any());
        verify(cartService, never()).clearCart(any());
        verify(cartService).releaseCheckoutLock(any(), any());
    }

    @Test
    void createOrder_paymentFailure_compensatesAndRethrows() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));
        when(productClient.getSnapshot("SKU1"))
                .thenReturn(new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00"));
        when(orderNumberGenerator.generate(any())).thenReturn("OR-20260819-00001");
        when(inventoryService.reserve(anyString(), anyInt(), anyString())).thenReturn(true);

        AtomicReference<Order> saved = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID()); // Hibernate assigns the id at persist
            saved.set(o);
            return o;
        });
        when(orderRepository.cancelIfActive(any(UUID.class))).thenReturn(1);
        when(orderItemRepository.findByOrderId(any(UUID.class))).thenReturn(List.of(item("SKU1", 2)));
        when(paymentClient.createPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new PaymentUnavailableException("down"));

        assertThrows(PaymentUnavailableException.class, () -> orderService.createOrder(onlineRequest(), "key-12345678"));

        UUID orderId = saved.get().getId();
        verify(orderRepository).cancelIfActive(orderId);
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.CANCELLED));
    }

    // ---- M1 idempotency payload compare ----

    @Test
    void createOrder_sameKeyDifferentPayload_rejectsWith409() {
        setAuth();
        String key = "key-12345678";
        CreateOrderRequest otherReq = CreateOrderRequest.builder()
                .shippingAddress("999 Other St").paymentMethod("COD").email("user@example.com").build();
        Order existing = Order.builder().id(UUID.randomUUID()).userId(userId).orderNumber("OR-EXISTING")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("50.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD")
                .idempotencyRequestHash(idemHash(userId, key, List.of(new CartService.CartItem("SKU1", 2)), otherReq))
                .build();
        when(orderRepository.findByUserIdAndIdempotencyKey(userId, key)).thenReturn(Optional.of(existing));
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));

        assertThrows(DuplicateResourceException.class, () -> orderService.createOrder(orderRequest(), key));
        verify(orderRepository, never()).save(any(Order.class));
        verify(cartService, never()).tryAcquireCheckoutLock(any());
    }

    @Test
    void createOrder_sameKeySameCartSamePayload_returnsExistingOrder() {
        setAuth();
        String key = "key-12345678";
        Order existing = Order.builder().id(UUID.randomUUID()).userId(userId).orderNumber("OR-EXISTING")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("50.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD")
                .idempotencyRequestHash(idemHash(userId, key, List.of(new CartService.CartItem("SKU1", 2)), orderRequest()))
                .build();
        when(orderRepository.findByUserIdAndIdempotencyKey(userId, key)).thenReturn(Optional.of(existing));
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));

        CheckoutResponse resp = orderService.createOrder(orderRequest(), key);

        assertEquals(existing.getId(), resp.getOrderId());
        verify(cartService, never()).tryAcquireCheckoutLock(any());
    }

    @Test
    void createOrder_sameKeyDifferentCart_rejectsWith409() {
        setAuth();
        String key = "key-12345678";
        // Order was placed with SKU1:2; the client then changed the cart to SKU2:3 and reuses the key.
        Order existing = Order.builder().id(UUID.randomUUID()).userId(userId).orderNumber("OR-EXISTING")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("50.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD")
                .idempotencyRequestHash(idemHash(userId, key, List.of(new CartService.CartItem("SKU1", 2)), orderRequest()))
                .build();
        when(orderRepository.findByUserIdAndIdempotencyKey(userId, key)).thenReturn(Optional.of(existing));
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU2", 3)));

        assertThrows(DuplicateResourceException.class, () -> orderService.createOrder(orderRequest(), key));
        verify(orderRepository, never()).save(any(Order.class));
        verify(cartService, never()).tryAcquireCheckoutLock(any());
    }

    @Test
    void createOrder_sameKeyEmptyCart_returnsExistingOrder() {
        setAuth();
        String key = "key-12345678";
        // The original checkout committed and cleared the cart; a response-lost retry hits an empty cart.
        Order existing = Order.builder().id(UUID.randomUUID()).userId(userId).orderNumber("OR-EXISTING")
                .status(OrderStatus.PENDING).totalAmount(new BigDecimal("50.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD")
                .idempotencyRequestHash(idemHash(userId, key, List.of(new CartService.CartItem("SKU1", 2)), orderRequest()))
                .build();
        when(orderRepository.findByUserIdAndIdempotencyKey(userId, key)).thenReturn(Optional.of(existing));
        when(cartService.readRawCart(null)).thenReturn(List.of());

        CheckoutResponse resp = orderService.createOrder(orderRequest(), key);

        assertEquals(existing.getId(), resp.getOrderId());
        verify(cartService, never()).tryAcquireCheckoutLock(any());
    }

    // ---- M5 completePaymentTx compensation ----

    @Test
    void createOrder_completePaymentTxFails_compensatesAndRethrows() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));
        when(productClient.getSnapshot("SKU1"))
                .thenReturn(new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00"));
        when(orderNumberGenerator.generate(any())).thenReturn("OR-20260823-00003");
        when(inventoryService.reserve(anyString(), anyInt(), anyString())).thenReturn(true);

        AtomicReference<Order> saved = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            saved.set(o);
            return o;
        });
        when(paymentClient.createPayment(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PaymentClient.PaymentResult(UUID.randomUUID(), "http://pay/vnpay",
                        Instant.now().plusSeconds(300), null));
        when(orderRepository.updatePaymentInfo(any(), any(), any()))
                .thenThrow(new RuntimeException("outbox db down"));
        when(orderRepository.cancelIfActive(any(UUID.class))).thenReturn(1);
        when(orderItemRepository.findByOrderId(any(UUID.class))).thenReturn(List.of(item("SKU1", 2)));

        assertThrows(RuntimeException.class, () -> orderService.createOrder(onlineRequest(), "key-12345678"));

        UUID orderId = saved.get().getId();
        verify(orderRepository).cancelIfActive(orderId);
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.CANCELLED
                && h.getOrderId().equals(orderId)));
        verify(cartService).releaseCheckoutLock(any(), any());
    }

    // ---- M7 post-commit failure must not break a committed order ----

    @Test
    void createOrder_removeItemsFails_stillReturnsCommittedOrder() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));
        when(productClient.getSnapshot("SKU1"))
                .thenReturn(new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00"));
        when(orderNumberGenerator.generate(any())).thenReturn("OR-20260823-00004");
        when(inventoryService.reserve(anyString(), anyInt(), anyString())).thenReturn(true);

        AtomicReference<Order> saved = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            saved.set(o);
            return o;
        });
        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(saved.get()));
        doThrow(new RuntimeException("redis down")).when(cartService).removeItems(eq(null), any());

        CheckoutResponse resp = orderService.createOrder(orderRequest(), "key-12345678");

        assertEquals(saved.get().getId(), resp.getOrderId());
        assertEquals("PENDING", resp.getStatus());
        // The committed order is returned despite the cart cleanup failure, and the lock still releases.
        verify(cartService).releaseCheckoutLock(any(), any());
    }

    // ---- COD timeout scheduler path ----

    @Test
    void expireCodPending_codPending_releasesStockEmitsCancelledEvent() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("200.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD").paymentGateway("COD").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.transition(orderId, "PENDING", "CANCELLED")).thenReturn(1);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        int rows = orderService.expireCodPending(orderId);

        assertEquals(1, rows);
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(historyRepository).save(argThat(h -> h.getNewStatus() == OrderStatus.CANCELLED
                && h.getOrderId().equals(orderId)));
        verify(outboxRepository).save(argThat(e -> "OrderCancelledEvent".equals(e.getEventType())
                && !e.getPublished()));
    }

    @Test
    void expireCodPending_alreadyNotPending_noOp() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(OrderStatus.CANCELLED)
                .totalAmount(new BigDecimal("200.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD").paymentGateway("COD").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        int rows = orderService.expireCodPending(orderId);

        assertEquals(0, rows);
        verify(orderRepository, never()).transition(any(), any(), any());
        verify(inventoryService, never()).release(anyString(), anyInt(), anyString());
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void expireCodPending_onlinePending_notTouched() {
        UUID orderId = UUID.randomUUID();
        // Online (VNPAY) PENDING — Payment-Service owns the TIMEOUT clock, this must not cancel it.
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("200.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("VNPAY_QR").paymentGateway("VNPAY").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        int rows = orderService.expireCodPending(orderId);

        assertEquals(0, rows);
        verify(orderRepository, never()).transition(any(), any(), any());
        verify(inventoryService, never()).release(anyString(), anyInt(), anyString());
    }

    @Test
    void expireCodPending_transitionLost_noDoubleRelease() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("200.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD").paymentGateway("COD").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.transition(orderId, "PENDING", "CANCELLED")).thenReturn(0); // admin won the race

        int rows = orderService.expireCodPending(orderId);

        assertEquals(0, rows);
        verify(inventoryService, never()).release(anyString(), anyInt(), anyString());
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    private String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Mirrors OrderService.idempotencyHash: canonical sorted sku:qty cart + request payload. */
    private String idemHash(UUID userId, String key, List<CartService.CartItem> cart, CreateOrderRequest req) {
        String cartPart = cart.stream()
                .sorted(java.util.Comparator.comparing(CartService.CartItem::sku))
                .map(c -> c.sku() + ":" + c.quantity())
                .collect(java.util.stream.Collectors.joining(","));
        return sha256(userId + "|" + key + "|" + cartPart + "|" + req.getEmail()
                + "|" + req.getShippingAddress() + "|" + req.getPaymentMethod());
    }

    // ---- cancelOrder ----

    @Test
    void cancelOrder_confirmed_releasesStockRefundsAndEmitsEvent() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        AtomicReference<Order> ref = orderRef(orderId, OrderStatus.CONFIRMED);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", resp.getStatus());
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(paymentClient).refund(eq(orderId), eq(ref.get().getPaymentTransactionId()));
        verify(outboxRepository).save(argThat(e -> "OrderCancelledEvent".equals(e.getEventType())));
    }

    @Test
    void cancelOrder_pending_releasesStockButNoRefundOrEvent() {
        setAuth();
        UUID orderId = UUID.randomUUID();
        orderRef(orderId, OrderStatus.PENDING);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));

        orderService.cancelOrder(orderId);

        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(paymentClient, never()).refund(any(), any());
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    // ---- adminUpdateStatus ----

    @Test
    void adminUpdateStatus_pendingToCancelled_releasesStock() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        orderRef(orderId, OrderStatus.PENDING);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.adminUpdateStatus(orderId, OrderStatus.CANCELLED, "test");

        assertEquals("CANCELLED", resp.getStatus());
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(paymentClient, never()).refund(any(), any());
        verify(outboxRepository).save(argThat(e -> "OrderCancelledEvent".equals(e.getEventType())));
    }

    @Test
    void adminUpdateStatus_confirmedToShipping_noStockChange() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        orderRef(orderId, OrderStatus.CONFIRMED);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.adminUpdateStatus(orderId, OrderStatus.SHIPPING, "test");

        assertEquals("SHIPPING", resp.getStatus());
        verify(inventoryService, never()).release(anyString(), anyInt(), anyString());
        verify(inventoryService, never()).deductReserved(anyString(), anyInt(), anyString());
        verify(outboxRepository).save(argThat(e -> "OrderStatusChangedEvent".equals(e.getEventType())));
    }

    @Test
    void adminUpdateStatus_shippingToDelivered_deductsReserved() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        orderRef(orderId, OrderStatus.SHIPPING);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.adminUpdateStatus(orderId, OrderStatus.DELIVERED, "test");

        assertEquals("DELIVERED", resp.getStatus());
        verify(inventoryService).deductReserved("SKU1", 2, "order:" + orderId);
        verify(outboxRepository).save(argThat(e -> "OrderStatusChangedEvent".equals(e.getEventType())));
    }

    @Test
    void adminUpdateStatus_confirmedToCancelled_refunds() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        AtomicReference<Order> ref = orderRef(orderId, OrderStatus.CONFIRMED);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.adminUpdateStatus(orderId, OrderStatus.CANCELLED, "test");

        assertEquals("CANCELLED", resp.getStatus());
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(paymentClient).refund(eq(orderId), eq(ref.get().getPaymentTransactionId()));
    }

    @Test
    void adminUpdateStatus_invalidTransition_throws() {
        setAuth();
        UUID orderId = UUID.randomUUID();
        orderRef(orderId, OrderStatus.PENDING);

        assertThrows(InvalidOrderStateException.class,
                () -> orderService.adminUpdateStatus(orderId, OrderStatus.DELIVERED, "test"));
        verify(orderRepository, never()).transition(any(), any(), any());
    }

    // ---- COD ----

    @Test
    void createOrder_cod_skipsPaymentClient_emitsOrderCreated_noPaymentUrl() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 2)));
        when(productClient.getSnapshot("SKU1"))
                .thenReturn(new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00"));
        when(orderNumberGenerator.generate(any())).thenReturn("OR-20260823-00001");
        when(inventoryService.reserve(anyString(), anyInt(), anyString())).thenReturn(true);

        AtomicReference<Order> saved = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID()); // Hibernate assigns the id at persist
            saved.set(o);
            return o;
        });
        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(saved.get()));

        CheckoutResponse resp = orderService.createOrder(orderRequest(), "key-12345678");

        assertEquals("PENDING", resp.getStatus());
        assertNotNull(resp.getOrderId());
        assertNull(resp.getPaymentUrl(), "COD order must not carry a payment URL");
        assertNull(resp.getQrImage(), "COD order must not carry a QR image");
        assertEquals("COD", saved.get().getPaymentMethod());
        assertEquals("COD", saved.get().getPaymentGateway());
        // No gateway transaction, no payment-info update, no QR.
        verify(paymentClient, never()).createPayment(any(), any(), any(), any(), any(), any(), any());
        verify(orderRepository, never()).updatePaymentInfo(any(), any(), any());
        // OrderCreatedEvent still emitted (paymentUrl empty) so Notification sends the confirmation email.
        verify(outboxRepository).save(argThat(e -> "OrderCreatedEvent".equals(e.getEventType())
                && !e.getPublished() && saved.get().getId().toString().equals(e.getAggregateId())));
        verify(cartService).removeItems(eq(null), any());
        verify(cartService).releaseCheckoutLock(any(), any());
    }

    @Test
    void createOrder_unsupportedPaymentMethod_rejected() {
        setAuth();
        CreateOrderRequest req = CreateOrderRequest.builder().shippingAddress("123 Main St").paymentMethod("BTC").build();

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(req, "key-12345678"));
        verify(cartService, never()).tryAcquireCheckoutLock(any());
    }

    @Test
    void createOrder_codPaymentMethod_withSpaces_normalizesAndSkipsGateway() {
        setAuth();
        when(orderRepository.findByUserIdAndIdempotencyKey(any(), any())).thenReturn(Optional.empty());
        when(cartService.tryAcquireCheckoutLock(any())).thenReturn("lock-token");
        when(cartService.readRawCart(null)).thenReturn(List.of(new CartService.CartItem("SKU1", 1)));
        when(productClient.getSnapshot("SKU1"))
                .thenReturn(new ProductSnapshot("SKU1", "T-Shirt", "Black/M", "100.00"));
        when(orderNumberGenerator.generate(any())).thenReturn("OR-20260823-00002");
        when(inventoryService.reserve(anyString(), anyInt(), anyString())).thenReturn(true);

        AtomicReference<Order> saved = new AtomicReference<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            saved.set(o);
            return o;
        });
        when(orderRepository.findById(any(UUID.class))).thenAnswer(inv -> Optional.of(saved.get()));

        CreateOrderRequest req = CreateOrderRequest.builder().shippingAddress("123 Main St").paymentMethod("  cod  ").build();
        CheckoutResponse resp = orderService.createOrder(req, "key-12345678");

        assertEquals("COD", saved.get().getPaymentMethod());
        assertNull(resp.getPaymentUrl());
        verify(paymentClient, never()).createPayment(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void adminUpdateStatus_pendingToConfirmed_codAllowed_emitsStatusEvent() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        orderRef(orderId, OrderStatus.PENDING); // orderRef builds a COD order (paymentMethod="COD")
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.adminUpdateStatus(orderId, OrderStatus.CONFIRMED, "merchant confirm");

        assertEquals("CONFIRMED", resp.getStatus());
        verify(outboxRepository).save(argThat(e -> "OrderStatusChangedEvent".equals(e.getEventType())));
    }

    @Test
    void adminUpdateStatus_pendingToConfirmed_onlineOrderRejected() {
        setAuth();
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("200.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("VNPAY_QR").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(InvalidOrderStateException.class,
                () -> orderService.adminUpdateStatus(orderId, OrderStatus.CONFIRMED, "test"));
        verify(orderRepository, never()).transition(any(), any(), any());
    }

    @Test
    void cancelConfirmedOrder_cod_skipsRefundButReleasesStock() throws Exception {
        setAuth();
        UUID orderId = UUID.randomUUID();
        // COD order has no payment transaction id → nothing to refund.
        Order order = Order.builder().id(orderId).userId(userId).orderNumber("OR-1").status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("200.00")).currency("VND")
                .shippingAddressSnapshot("addr").paymentMethod("COD").build();
        AtomicReference<Order> ref = new AtomicReference<>(order);
        when(orderRepository.findById(orderId)).thenAnswer(inv -> Optional.of(ref.get()));
        when(orderRepository.cancelIfActive(orderId)).thenAnswer(inv -> {
            ref.get().setStatus(OrderStatus.CANCELLED);
            return 1;
        });
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(item("SKU1", 2)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        OrderResponse resp = orderService.cancelOrder(orderId);

        assertEquals("CANCELLED", resp.getStatus());
        verify(inventoryService).release("SKU1", 2, "order:" + orderId);
        verify(paymentClient, never()).refund(any(), any());
        verify(outboxRepository).save(argThat(e -> "OrderCancelledEvent".equals(e.getEventType())));
    }
}
