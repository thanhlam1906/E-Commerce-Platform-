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
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OrderService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final String PAYMENT_GATEWAY = "VNPAY";

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

        if (idempotencyKey != null) {
            Order existing = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).orElse(null);
            if (existing != null) {
                if (existing.getStatus() == OrderStatus.CANCELLED || existing.getStatus() == OrderStatus.EXPIRED) {
                    throw new IllegalArgumentException(ErrorMessages.IDEMPOTENCY_KEY_EXPIRED);
                }
                return toCheckoutResponse(existing);
            }
        }

        // Serialize checkout per user so two concurrent POST /orders cannot both read the same
        // cart, double-reserve and double-charge. Released in finally (or Redis TTL expires).
        if (!cartService.tryAcquireCheckoutLock(userId)) {
            throw new IllegalStateException(ErrorMessages.CHECKOUT_IN_PROGRESS);
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

            try {
                String returnUrl = (paymentReturnUrl == null || paymentReturnUrl.isBlank()) ? null : paymentReturnUrl;
                PaymentClient.PaymentResult payment = paymentClient.createPayment(
                        order.getId(), userId, order.getTotalAmount(), "VND",
                        order.getPaymentMethod(), PAYMENT_GATEWAY, returnUrl);
                transactionTemplate.executeWithoutResult(s -> completePaymentTx(order, payment));
                // Remove only the ordered SKUs — items the user added during the payment call survive.
                cartService.removeItems(null, cart.stream().map(CartService.CartItem::sku).toList());
                return toCheckoutResponse(orderRepository.findById(order.getId()).orElseThrow());
            } catch (PaymentUnavailableException e) {
                transactionTemplate.executeWithoutResult(s -> compensateTx(order.getId(), userId));
                throw e;
            }
        } finally {
            cartService.releaseCheckoutLock(userId);
        }
    }

    private Order createOrderTx(UUID userId, CreateOrderRequest req, String idempotencyKey,
                                List<CartService.CartItem> cart, Map<String, ProductSnapshot> snapshots) {
        UUID orderId = UUID.randomUUID();
        String orderNumber = orderNumberGenerator.generate(orderRepository::existsByOrderNumber);

        List<String> outOfStock = new ArrayList<>();
        for (CartService.CartItem item : cart) {
            if (!inventoryService.reserve(item.sku(), item.quantity(), "order:" + orderId)) {
                outOfStock.add(item.sku());
            }
        }
        if (!outOfStock.isEmpty()) {
            throw new InsufficientStockException(outOfStock);
        }

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
                .id(orderId).userId(userId).email(req.getEmail()).orderNumber(orderNumber).status(OrderStatus.PENDING)
                .totalAmount(totalAmount).currency("VND")
                .shippingAddressSnapshot(req.getShippingAddress())
                .paymentMethod(req.getPaymentMethod()).paymentGateway(PAYMENT_GATEWAY)
                .idempotencyKey(idempotencyKey)
                .idempotencyRequestHash(sha256(userId + "|" + idempotencyKey + "|" + req.getShippingAddress() + req.getPaymentMethod()))
                .orderItems(items).build();
        for (OrderItem item : items) {
            item.setOrder(order);
        }
        orderRepository.save(order);

        historyRepository.save(OrderStatusHistory.builder().orderId(orderId)
                .oldStatus(null).newStatus(OrderStatus.PENDING).changedBy(userId).reason("Order created").build());

        outboxRepository.save(OutboxEvent.builder().eventType("OrderCreatedEvent")
                .aggregateId(orderId.toString()).payload(orderCreatedPayload(order, null)).published(false).build());
        return order;
    }

    private void completePaymentTx(Order order, PaymentClient.PaymentResult payment) {
        orderRepository.updatePaymentInfo(order.getId(), payment.paymentUrl(), payment.transactionId());
        // Keep published=false so OutboxPublisher relays it (with the payment URL) to Kafka.
        outboxRepository.findByEventTypeAndAggregateId("OrderCreatedEvent", order.getId().toString())
                .ifPresent(event -> {
                    event.setPayload(orderCreatedPayload(order, payment.paymentUrl()));
                    outboxRepository.save(event);
                });
    }

    private void compensateTx(UUID orderId, UUID userId) {
        if (orderRepository.cancelIfActive(orderId) == 1) {
            for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
                inventoryService.release(item.getSku(), item.getQuantity(), "order:" + orderId);
            }
            historyRepository.save(OrderStatusHistory.builder().orderId(orderId)
                    .oldStatus(OrderStatus.PENDING).newStatus(OrderStatus.CANCELLED)
                    .changedBy(userId).reason("Payment unavailable, auto-cancelled").build());
            // Drop the unpublished OrderCreatedEvent so a cancelled order is never announced.
            outboxRepository.deleteByEventTypeAndAggregateId("OrderCreatedEvent", orderId.toString());
        }
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
            case PENDING -> target == OrderStatus.CANCELLED;
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
        try {
            paymentClient.refund(order.getId(), order.getPaymentTransactionId());
        } catch (PaymentUnavailableException e) {
            log.warn("Refund failed for order {}, will need out-of-band handling", order.getId(), e);
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

    private void validateIdempotencyKey(String key) {
        if (key != null && !IDEMPOTENCY_KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(ErrorMessages.INVALID_IDEMPOTENCY_KEY);
        }
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
