package com.voltstack.ecommerce.order.repository;

import com.voltstack.ecommerce.order.entity.Order;
import com.voltstack.ecommerce.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByOrderNumber(String orderNumber);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, java.time.Instant createdAt);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Page<Order> findByUserIdAndStatus(UUID userId, OrderStatus status, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE orders SET status = :target, updated_at = now() WHERE id = :id AND status = :expected", nativeQuery = true)
    int transition(@Param("id") UUID id, @Param("expected") String expected, @Param("target") String target);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE orders SET status = 'CANCELLED', updated_at = now() WHERE id = :id AND status IN ('PENDING','CONFIRMED')", nativeQuery = true)
    int cancelIfActive(@Param("id") UUID id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE orders SET payment_url = :paymentUrl, payment_transaction_id = :txnId, updated_at = now() WHERE id = :id", nativeQuery = true)
    int updatePaymentInfo(@Param("id") UUID id, @Param("paymentUrl") String paymentUrl, @Param("txnId") UUID txnId);
}
