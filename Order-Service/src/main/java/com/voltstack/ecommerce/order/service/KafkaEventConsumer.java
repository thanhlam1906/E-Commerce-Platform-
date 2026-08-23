package com.voltstack.ecommerce.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltstack.ecommerce.order.entity.ConsumedEvent;
import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderItem;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import com.voltstack.ecommerce.order.entity.OrderStatusHistory;
import com.voltstack.ecommerce.order.entity.OutboxEvent;
import com.voltstack.ecommerce.order.repository.ConsumedEventRepository;
import com.voltstack.ecommerce.order.repository.OrderItemRepository;
import com.voltstack.ecommerce.order.repository.OrderRepository;
import com.voltstack.ecommerce.order.repository.OrderStatusHistoryRepository;
import com.voltstack.ecommerce.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventConsumer {

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final ConsumedEventRepository consumedEventRepository;
    private final InventoryService inventoryService;
    private final OutboxRepository outboxRepository;

    /**
     * Whole method is one transaction so the guarded status transition + release +
     * consumed_events row are atomic. Business errors roll back and Kafka redelivers;
     * malformed events are skipped (committed) so one poison message cannot wedge a partition.
     */
    @KafkaListener(topics = "${order.payment-events-topic}", groupId = "order-service")
    @Transactional
    public void onPaymentEvent(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            UUID eventId = UUID.fromString(text(node, "eventId", "event_id"));
            UUID orderId = UUID.fromString(text(node, "orderId", "order_id"));
            String action = normalize(text(node, "eventType", "event_type"));
            handle(action, eventId, orderId);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.error("Malformed payment event, skipping: {}", message, e);
        }
    }

    private void handle(String action, UUID eventId, UUID orderId) {
        if (consumedEventRepository.existsById(eventId)) {
            return;
        }
        int rows;
        if ("REFUNDED".equals(action)) {
            // Order already transitioned via its own cancel path; the payment-side refund is a no-op here.
            log.info("Payment refunded event for order {} — no Order state change", orderId);
            rows = 0;
        } else {
            rows = switch (action) {
                case "COMPLETED" -> orderRepository.transition(orderId, "PENDING", "CONFIRMED");
                case "FAILED" -> orderRepository.transition(orderId, "PENDING", "CANCELLED");
                case "TIMEOUT" -> orderRepository.transition(orderId, "PENDING", "EXPIRED");
                default -> throw new IllegalArgumentException("Unknown payment event type: " + action);
            };
        }
        if (rows == 1) {
            OrderStatus newStatus = switch (action) {
                case "COMPLETED" -> OrderStatus.CONFIRMED;
                case "FAILED" -> OrderStatus.CANCELLED;
                default -> OrderStatus.EXPIRED;
            };
            if (action.equals("FAILED") || action.equals("TIMEOUT")) {
                releaseStock(orderId);
            }
            historyRepository.save(OrderStatusHistory.builder()
                    .orderId(orderId).oldStatus(OrderStatus.PENDING).newStatus(newStatus)
                    .changedBy(null).reason("Payment event: " + action).build());
            // Notify on every payment outcome, not just success: FAILED/TIMEOUT must also
            // produce an email (cancelled / expired), or users never hear their order died.
            emitOrderStatusEvent(orderId, newStatus);
        }
        consumedEventRepository.save(ConsumedEvent.builder().eventId(eventId).eventType(action).build());
    }

    /** Order confirmed is the event Notification consumes ("order confirmed" email).
     *  Notification drops OrderStatusChangedEvent without a recipient email (dead letter),
     *  so the payload must carry email + userId + oldStatus + newStatus like OrderService.emitOutbox. */
    private void emitOrderStatusEvent(UUID orderId, OrderStatus status) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalStateException("Order not found for status event: " + orderId));
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventId", UUID.randomUUID().toString());
            payload.put("eventType", "OrderStatusChangedEvent");
            payload.put("orderId", orderId.toString());
            payload.put("email", order.getEmail());
            payload.put("userId", order.getUserId().toString());
            // transition() already ran, so order.getStatus() is the new status — old is always PENDING.
            payload.put("oldStatus", OrderStatus.PENDING.name());
            payload.put("newStatus", status.name());
            payload.put("status", status.name());
            outboxRepository.save(OutboxEvent.builder().eventType("OrderStatusChangedEvent")
                    .aggregateId(orderId.toString())
                    .payload(objectMapper.writeValueAsString(payload))
                    .published(false).build());
        } catch (JsonProcessingException e) {
            log.error("Failed to build OrderStatusChangedEvent for order {}", orderId, e);
        }
    }

    private void releaseStock(UUID orderId) {
        for (OrderItem item : orderItemRepository.findByOrderId(orderId)) {
            inventoryService.release(item.getSku(), item.getQuantity(), "order:" + orderId);
        }
    }

    private String text(JsonNode node, String camel, String snake) {
        JsonNode v = node.has(camel) ? node.get(camel) : node.get(snake);
        return v == null ? "" : v.asText();
    }

    /** "PaymentCompleted", "payment_completed", "PaymentCompletedEvent" → "COMPLETED" */
    private String normalize(String type) {
        String t = type.replace("_", "").toUpperCase();
        if (t.contains("COMPLETED")) return "COMPLETED";
        if (t.contains("FAILED")) return "FAILED";
        if (t.contains("TIMEOUT")) return "TIMEOUT";
        return t;
    }
}
