package com.voltstack.ecommerce.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.order.client.PaymentClient;
import com.voltstack.ecommerce.order.client.ProductClient;
import com.voltstack.ecommerce.order.client.ProductSnapshot;
import com.voltstack.ecommerce.order.constant.ErrorMessages;
import com.voltstack.ecommerce.order.dto.request.CreateOrderRequest;
import com.voltstack.ecommerce.order.dto.response.CheckoutResponse;
import com.voltstack.ecommerce.order.dto.response.OrderHistoryResponse;
import com.voltstack.ecommerce.order.dto.response.OrderItemResponse;
import com.voltstack.ecommerce.order.dto.response.OrderResponse;
import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderItem;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.entity.OrderStatusHistory;
import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.exception.CheckoutInProgressException;
import com.voltstack.ecommerce.order.exception.DuplicateResourceException;
import com.voltstack.ecommerce.order.exception.InsufficientStockException;
import com.voltstack.ecommerce.order.exception.InvalidOrderStateException;
import com.voltstack.ecommerce.order.exception.PaymentUnavailableException;
import com.voltstack.ecommerce.order.exception.ResourceNotFoundException;
import com.voltstack.ecommerce.order.repository.OrderItemRepository;
import com.voltstack.ecommerce.order.repository.OrderRepository;
import com.voltstack.ecommerce.order.repository.OrderStatusHistoryRepository;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import com.voltstack.ecommerce.order.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final String PAYMENT_GATEWAY = "VNPAY";
    private static final String PAYMENT_METHOD_COD = "COD";
    private static final String PAYMENT_METHOD_VNPAY_QR = "VNPAY_QR";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OutboxRepository outboxRepository;
    private final InventoryService inventoryService;
    private final CartService cartService;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final OrderNumberGenerator orderNumberGenerator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /** Optional client return URL forwarded to Payment-Service (SRS §3); blank → Payment decides. */
    @Value("${payment.return-url:}")
    private String paymentReturnUrl;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository,
                        OrderStatusHistoryRepository historyRepository, OutboxRepository outboxRepository,
                        InventoryService inventoryService, CartService cartService,
                        ProductClient productClient, PaymentClient paymentClient,
                        OrderNumberGenerator orderNumberGenerator, ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.historyRepository = historyRepository;
        this.outboxRepository = outboxRepository;
        this.inventoryService = inventoryService;
        this.cartService = cartService;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
        this.orderNumberGenerator = orderNumberGenerator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---- Checkout ----

    public CheckoutResponse createOrder(CreateOrderRequest req, String idempotencyKey) {
        UUID userId = SecurityUtils.requireUserId();
        validateIdempotencyKey(idempotencyKey);
        req.setPaymentMethod(normalizePaymentMethod(req.getPaymentMethod()));

        if (idempotencyKey != null) {
            Order existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == OrderStatus.CANCELLED || existing.getStatus() == OrderStatus.EXPIRED) {
                    throw new IllegalArgumentException(ErrorMessages.IDEMPOTENCY_KEY_EXPIRED);
                }
                // M1: same key but different payload is a client bug — reject, never return the stale
                // order nor overwrite it. (Hash is null only for rows created before M1 shipped.)
                // The hash covers the cart (sku:qty → amount) + email, so reusing a key after
                // changing the cart/payload 409s instead of returning the stale order.
                if (existing.getIdempotencyRequestHash() != null) {
                    List<CartService.CartItem> cart = cartService.readRawCart(null);
                    // Empty cart = the original checkout already cleared it (or nothing new was added),
                    // so this is a retry, not a changed payload — return the same order.
                    if (!cart.isEmpty()
                            && !existing.getIdempotencyRequestHash().equals(
                                    idempotencyHash(userId, idempotencyKey, req, cart))) {
                        throw new DuplicateResourceException(ErrorMessages.IDEMPOTENCY_KEY_MISMATCH);
                    }
                }
                return toCheckoutResponse(existing);
            }
        }

        // Serialize checkout per user so two concurrent POST /orders cannot both read the same
        // cart, double-reserve and double-charge. Released in finally (or Redis TTL expires).
        String lockToken = cartService.tryAcquireCheckoutLock(userId);
        if (lockToken == null) {
            throw new CheckoutInProgressException(ErrorMessages.CHECKOUT_IN_PROGRESS);
        }
        try {
            List<CartService.CartItem> cart = cartService.readRawCart(null);
            if (cart.isEmpty()) {
                throw new IllegalArgumentException(ErrorMessages.CART_EMPTY);
            }

            // Verify + snapshot each SKU before touching inventory (SRS flow step 4).
            Map<String, ProductSnapshot> snapshots = new HashMap<>();
            for (CartService.CartItem item : cart) {
                snapshots.put(item.sku(), productClient.getSnapshot(item.sku()));
            }

            // Commit-before-payment per SRS: reserve + insert order/outbox in one tx, then call Payment.
            Order order = transactionTemplate.execute(status -> createOrderTx(userId, req, idempotencyKey, cart, snapshots));
            if (order == null) {
                throw new IllegalStateException("Không thể tạo đơn hàng");
            }

            String qrImage = null;
            if (isCod(order.getPaymentMethod())) {
                // COD: no gateway transaction, no payment URL/QR. Order stays PENDING awaiting merchant
                // (admin) confirmation; emit OrderCreatedEvent now — nothing later enriches it with a URL.
                transactionTemplate.executeWithoutResult(s -> outboxRepository.save(OutboxEvent.builder()
                        .eventType("OrderCreatedEvent").aggregateId(order.getId().toString())
                        .payload(orderCreatedPayload(order, null)).published(false).build()));
            } else {
                // M5: order + stock are already committed. If the payment wiring (HTTP create + outbox)
                // fails we must compensate — release stock + cancel — or the order stays PENDING with a
                // live Payment txn and reserved stock forever.
                try {
                    String returnUrl = (paymentReturnUrl == null || paymentReturnUrl.isBlank()) ? null : paymentReturnUrl;
                    PaymentClient.PaymentResult payment = paymentClient.createPayment(
                            order.getId(), userId, order.getTotalAmount(), "VND",
                            order.getPaymentMethod(), PAYMENT_GATEWAY, returnUrl);
                    transactionTemplate.executeWithoutResult(s -> completePaymentTx(order, payment));
                    qrImage = payment.qrImage();
                } catch (RuntimeException e) {
                    log.error("Payment wiring failed for order {}, compensating", order.getId(), e);
                    transactionTemplate.executeWithoutResult(s -> compensateTx(order.getId(), userId));
                    throw e;
                }
            }
            // M7: order is committed — a cart-cleanup hiccup must not turn a successful checkout into a 500.
            try {
                // Remove only the ordered SKUs — items the user added during the payment call survive.
                cartService.removeItems(null, cart.stream().map(CartService.CartItem::sku).toList());
            } catch (RuntimeException e) {
                log.warn("Failed to remove ordered SKUs from cart for order {}, order is committed", order.getId(), e);
            }
            CheckoutResponse response = toCheckoutResponse(orderRepository.findById(order.getId()).orElseThrow());
            // Not persisted — the QR is for the immediate post-checkout "quét mã QR" screen (COD has none).
            response.setQrImage(qrImage);
            return response;
        } finally {
            cartService.releaseCheckoutLock(userId, lockToken);
        }
    }

    private Order createOrderTx(UUID userId, CreateOrderRequest req, String idempotencyKey,
                                List<CartService.CartItem> cart, Map<String, ProductSnapshot> snapshots) {
        String orderNumber = orderNumberGenerator.generate(orderRepository::existsByOrderNumber);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (CartService.CartItem item : cart) {
            ProductSnapshot snap = snapshots.get(item.sku());
            BigDecimal unitPrice = snap.unitPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            totalAmount = totalAmount.add(subtotal);
            items.add(OrderItem.builder().sku(item.sku()).productName(snap.productName())
                    .variantName(snap.variantName()).unitPrice(unitPrice).quantity(item.quantity())
                    .subtotal(subtotal).build());
        }

        Order order = Order.builder()
                .userId(userId).email(req.getEmail()).orderNumber(orderNumber).status(OrderStatus.PENDING)
                .totalAmount(totalAmount).currency("VND")
                .shippingAddressSnapshot(req.getShippingAddress())
                .paymentMethod(req.getPaymentMethod()).paymentGateway(isCod(req.getPaymentMethod()) ? "COD" : PAYMENT_GATEWAY)
                .idempotencyKey(idempotencyKey)
                .idempotencyRequestHash(idempotencyKey == null ? null : idempotencyHash(userId, idempotencyKey, req, cart))
                .orderItems(items).build();
        for (OrderItem item : items) {
            item.setOrder(order);
        }
        orderRepository.save(order); // persist assigns the UUID; every reserve/release reference below uses it

        List<String> outOfStock = new ArrayList<>();
        for (CartService.CartItem item : cart) {
            if (!inventoryService.reserve(item.sku(), item.quantity(), "order:" + order.getId())) {
                outOfStock.add(item.sku());
            }
        }
        if (!outOfStock.isEmpty()) {
            throw new InsufficientStockException(outOfStock); // rolls back the insert and any earlier reserves
        }

        historyRepository.save(OrderStatusHistory.builder().orderId(order.getId())
                .oldStatus(null).newStatus(OrderStatus.PENDING).changedBy(userId).reason("Order created").build());
        return order;
    }

    private void completePaymentTx(Order order, PaymentClient.PaymentResult payment) {
        orderRepository.updatePaymentInfo(order.getId(), payment.paymentUrl(), payment.transactionId());
        // Write the OrderCreatedEvent only now that the payment URL is known, so the relay never
        // publishes an event with an empty link during the payment-call window.
        outboxRepository.save(OutboxEvent.builder().eventType("OrderCreatedEvent")
                .aggregateId(order.getId().toString())
                .payload(orderCreatedPayload(order, payment.paymentUrl())).published(false).build());
    }

    private void compensateTx(UUID orderId, UUID userId) {
        if (orderRepository.cancelIfActive(orderId) == 1) {
            for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
                inventoryService.release(item.getSku(), item.getQuantity(), "order:" + orderId);
            }
            historyRepository.save(OrderStatusHistory.builder().orderId(orderId)
                    .oldStatus(OrderStatus.PENDING).newStatus(OrderStatus.CANCELLED)
                    .changedBy(userId).reason("Payment unavailable, auto-cancelled").build());
        }
    }

    /**
     * Cancel a COD order still PENDING past the merchant-confirmation window (COD timeout scheduler).
     * Reuses the same release + history + outbox path as the admin cancel. Idempotent: the guarded
     * PENDING→CANCELLED transition means a concurrent admin cancel / payment event wins and we no-op.
     * ponytail: only COD — online PENDING expiry is owned by Payment-Service (TIMEOUT), running two
     * clocks on the same order would double-expire.
     */
    @Transactional
    public int expireCodPending(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING || !"COD".equals(order.getPaymentGateway())) {
            return 0;
        }
        if (orderRepository.transition(orderId, "PENDING", "CANCELLED") == 0) {
            return 0;
        }
        for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
            inventoryService.release(item.getSku(), item.getQuantity(), "order:" + orderId);
        }
        historyRepository.save(history(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED, "COD quá hạn chờ xác nhận"));
        emitOutbox("OrderCancelledEvent", order, OrderStatus.CANCELLED, "COD pending timeout");
        return 1;
    }

    // ---- Reads ----

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMyOrders(OrderStatus status, Pageable pageable) {
        UUID userId = SecurityUtils.requireUserId();
        Page<Order> page = status == null
                ? orderRepository.findByUserId(userId, pageable)
                : orderRepository.findByUserIdAndStatus(userId, status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listAllOrders(OrderStatus status, Pageable pageable) {
        Page<Order> page = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId) {
        Order order = requireOwnOrAdmin(orderId);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderHistoryResponse> getHistory(UUID orderId) {
        requireOwnOrAdmin(orderId);
        return historyRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(h -> OrderHistoryResponse.builder()
                        .id(h.getId()).orderId(h.getOrderId())
                        .oldStatus(h.getOldStatus() == null ? null : h.getOldStatus().name())
                        .newStatus(h.getNewStatus().name()).changedBy(h.getChangedBy())
                        .reason(h.getReason()).createdAt(h.getCreatedAt()).build())
                .toList();
    }

    // ---- Mutations ----

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = requireOwnOrAdmin(orderId);
        UUID userId = SecurityUtils.requireUserId();
        OrderStatus oldStatus = order.getStatus();
        if (orderRepository.cancelIfActive(orderId) == 1) {
            for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
                inventoryService.release(item.getSku(), item.getQuantity(), "order:" + orderId);
            }
            historyRepository.save(OrderStatusHistory.builder().orderId(orderId)
                    .oldStatus(oldStatus).newStatus(OrderStatus.CANCELLED).changedBy(userId).reason("User cancel").build());
            if (oldStatus == OrderStatus.CONFIRMED) {
                refundQuietly(order);
                emitOutbox("OrderCancelledEvent", order, OrderStatus.CANCELLED, "User cancel");
            }
        }
        return toResponse(orderRepository.findById(orderId).orElseThrow());
    }

    @Transactional
    public OrderResponse adminUpdateStatus(UUID orderId, OrderStatus target, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ORDER_NOT_FOUND));
        OrderStatus current = order.getStatus();
        boolean valid = switch (current) {
            // Online orders leave PENDING only via the payment COMPLETED event; a COD order waits for
            // merchant (admin) confirmation — the Shopee-style "chờ xác nhận" step.
            case PENDING -> target == OrderStatus.CANCELLED
                    || (target == OrderStatus.CONFIRMED && isCod(order.getPaymentMethod()));
            case CONFIRMED -> target == OrderStatus.SHIPPING || target == OrderStatus.CANCELLED;
            case SHIPPING -> target == OrderStatus.DELIVERED;
            default -> false;
        };
        if (!valid) {
            throw new InvalidOrderStateException("Không thể chuyển trạng thái từ " + current + " sang " + target);
        }
        if (orderRepository.transition(orderId, current.name(), target.name()) == 0) {
            throw new InvalidOrderStateException("Trạng thái đơn đã thay đổi, vui lòng thử lại");
        }

        switch (target) {
            case CONFIRMED -> {
                historyRepository.save(history(orderId, current, target, reason));
                emitOutbox("OrderStatusChangedEvent", order, target, reason);
            }
            case DELIVERED -> {
                for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
                    inventoryService.deductReserved(item.getSku(), item.getQuantity(), "order:" + orderId);
                }
                historyRepository.save(history(orderId, current, target, reason));
                emitOutbox("OrderStatusChangedEvent", order, target, reason);
            }
            case CANCELLED -> {
                for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
                    inventoryService.release(item.getSku(), item.getQuantity(), "order:" + orderId);
                }
                if (current == OrderStatus.CONFIRMED) {
                    refundQuietly(order);
                }
                historyRepository.save(history(orderId, current, target, reason));
                emitOutbox("OrderCancelledEvent", order, target, reason);
            }
            case SHIPPING -> {
                historyRepository.save(history(orderId, current, target, reason));
                emitOutbox("OrderStatusChangedEvent", order, target, reason);
            }
            default -> throw new InvalidOrderStateException("Trạng thái chuyển đổi không được hỗ trợ");
        }
        return toResponse(orderRepository.findById(orderId).orElseThrow());
    }

    // ---- Helpers ----

    private Order requireOwnOrAdmin(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.ORDER_NOT_FOUND));
        UUID userId = SecurityUtils.currentUserId();
        if ((userId == null || !order.getUserId().equals(userId)) && !SecurityUtils.isAdmin()) {
            throw new ResourceNotFoundException(ErrorMessages.ORDER_NOT_YOURS);
        }
        return order;
    }

    private OrderStatusHistory history(UUID orderId, OrderStatus oldStatus, OrderStatus newStatus, String reason) {
        return OrderStatusHistory.builder().orderId(orderId).oldStatus(oldStatus).newStatus(newStatus)
                .changedBy(SecurityUtils.currentUserId()).reason(reason).build();
    }

    private void refundQuietly(Order order) {
        // COD orders have no payment transaction (payment_gateway='COD') — nothing to refund.
        if (order.getPaymentTransactionId() == null) {
            return;
        }
        // M8: fire the HTTP refund only after this DB tx commits, so a slow Payment call never holds
        // the orders row lock. On rollback the refund is skipped (the cancel did not persist). Outside
        // a tx (unit tests) it runs inline.
        Runnable refund = () -> {
            try {
                paymentClient.refund(order.getId(), order.getPaymentTransactionId());
            } catch (PaymentUnavailableException e) {
                log.warn("Refund failed for order {}, will need out-of-band handling", order.getId(), e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refund.run();
                }
            });
        } else {
            refund.run();
        }
    }

    private void emitOutbox(String eventType, Order order, OrderStatus newStatus, String reason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", UUID.randomUUID().toString());
            payload.put("eventType", eventType);
            payload.put("orderId", order.getId().toString());
            payload.put("orderNumber", order.getOrderNumber());
            payload.put("email", order.getEmail());
            payload.put("userId", order.getUserId().toString());
            // order.getStatus() is the pre-transition status: every caller fetches the order
            // before the DB transition, so it still holds the old status here.
            payload.put("oldStatus", order.getStatus().name());
            payload.put("newStatus", newStatus.name());
            if (reason != null && !reason.isBlank()) {
                payload.put("reason", reason);
            }
            outboxRepository.save(OutboxEvent.builder().eventType(eventType)
                    .aggregateId(order.getId().toString())
                    .payload(objectMapper.writeValueAsString(payload))
                    .published(false).build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể tạo outbox payload", e);
        }
    }

    private String orderCreatedPayload(Order order, String paymentUrl) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", UUID.randomUUID().toString());
            payload.put("eventType", "OrderCreatedEvent");
            payload.put("orderId", order.getId().toString());
            payload.put("orderNumber", order.getOrderNumber());
            payload.put("totalAmount", order.getTotalAmount());
            payload.put("currency", order.getCurrency());
            payload.put("status", OrderStatus.PENDING.name());
            payload.put("paymentUrl", paymentUrl == null ? "" : paymentUrl);
            payload.put("email", order.getEmail());
            payload.put("userId", order.getUserId().toString());
            payload.put("items", order.getOrderItems().stream()
                    .map(i -> Map.of(
                            "productName", i.getProductName(),
                            "quantity", i.getQuantity(),
                            "subtotal", i.getSubtotal()))
                    .toList());
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Không thể tạo outbox payload", e);
        }
    }

    private CheckoutResponse toCheckoutResponse(Order order) {
        return CheckoutResponse.builder()
                .orderId(order.getId()).orderNumber(order.getOrderNumber())
                .status(order.getStatus().name()).paymentUrl(order.getPaymentUrl())
                .totalAmount(order.getTotalAmount()).build();
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(i -> OrderItemResponse.builder().sku(i.getSku()).productName(i.getProductName())
                        .variantName(i.getVariantName()).unitPrice(i.getUnitPrice()).quantity(i.getQuantity())
                        .subtotal(i.getSubtotal()).build())
                .toList();
        return OrderResponse.builder()
                .id(order.getId()).userId(order.getUserId()).orderNumber(order.getOrderNumber())
                .status(order.getStatus().name()).totalAmount(order.getTotalAmount()).currency(order.getCurrency())
                .shippingAddressSnapshot(order.getShippingAddressSnapshot()).paymentMethod(order.getPaymentMethod())
                .paymentUrl(order.getPaymentUrl()).createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt())
                .items(items).build();
    }

    /** COD is stored as-is on the order (payment_method) and also in payment_gateway (column is NOT NULL). */
    private static boolean isCod(String paymentMethod) {
        return PAYMENT_METHOD_COD.equals(paymentMethod);
    }

    /** Whichever payment method a client sends, normalize to the two supported values before persisting. */
    private static String normalizePaymentMethod(String pm) {
        String normalized = pm == null ? "" : pm.trim().toUpperCase();
        if (!PAYMENT_METHOD_COD.equals(normalized) && !PAYMENT_METHOD_VNPAY_QR.equals(normalized)) {
            throw new IllegalArgumentException(ErrorMessages.PAYMENT_METHOD_UNSUPPORTED);
        }
        return normalized;
    }

    private void validateIdempotencyKey(String key) {
        if (key != null && !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_IDEMPOTENCY_KEY);
        }
    }

    private String idempotencyHash(UUID userId, String idempotencyKey, CreateOrderRequest req,
                                   List<CartService.CartItem> cart) {
        // Canonical cart form (sorted sku:qty) so a key reused after the cart changed hashes differently.
        String cartPart = cart.stream()
                .sorted(Comparator.comparing(CartService.CartItem::sku))
                .map(c -> c.sku() + ":" + c.quantity())
                .collect(Collectors.joining(","));
        return sha256(userId + "|" + idempotencyKey + "|" + cartPart + "|" + req.getEmail()
                + "|" + req.getShippingAddress() + "|" + req.getPaymentMethod());
    }

    private String sha256(String input) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
