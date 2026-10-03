package com.realtimeorder.order.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Order aggregate: Order + items + status history. Mọi thay đổi trạng thái đi qua {@link #transition},
 * nơi kiểm tra state machine và ghi history.
 *
 * Không có setter cho status: code bên ngoài không thể bỏ qua state machine.
 */
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    @Column(name = "total_amount", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * {@code @BatchSize}: khi map một trang order, Hibernate load items của nhiều order trong một
     * câu {@code WHERE order_id IN (...)} thay vì một câu cho mỗi order (N+1 query).
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("productName")
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    @OrderBy("createdAt")
    private List<OrderStatusHistory> history = new ArrayList<>();

    protected Order() {
        // JPA
    }

    private Order(UUID id, UUID customerId, UUID restaurantId, List<OrderItem> items, Instant now) {
        super(id);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Order phải có ít nhất một item");
        }
        this.customerId = Objects.requireNonNull(customerId, "customerId");
        this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId");
        // Cùng thứ tự với @OrderBy khi load từ DB, để response lúc tạo và lúc đọc lại giống nhau.
        items.stream().sorted(Comparator.comparing(OrderItem::getProductName)).forEach(this.items::add);
        this.totalAmount = items.stream().map(OrderItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
        this.history.add(new OrderStatusHistory(null, OrderStatus.CREATED, Actor.CUSTOMER, customerId, now));
    }

    /**
     * @param id ID đã sinh trước, vì inventory được reserve theo orderId trước khi tạo Order
     */
    public static Order place(UUID id, UUID customerId, UUID restaurantId, List<OrderItem> items, Instant now) {
        return new Order(id, customerId, restaurantId, items, now);
    }

    /** Restaurant confirm: chỉ khi CREATED (ADR-031). */
    public void confirm(UUID restaurantUserId, Instant now) {
        transition(OrderStatus.CONFIRMED, Actor.RESTAURANT, restaurantUserId, now);
    }

    /** Restaurant reject: chỉ khi CREATED (ADR-031). Chưa có payment nên không có refund. */
    public void reject(UUID restaurantUserId, Instant now) {
        if (status != OrderStatus.CREATED) {
            throw new InvalidOrderTransitionException(status, OrderStatus.CANCELLED);
        }
        cancel(CancelReason.RESTAURANT_REJECTED, Actor.RESTAURANT, restaurantUserId, now);
    }

    /** Customer cancel: chỉ khi CREATED hoặc CONFIRMED (ADR-024). */
    public void cancelByCustomer(UUID customerId, Instant now) {
        if (status != OrderStatus.CREATED && status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderTransitionException(status, OrderStatus.CANCELLED);
        }
        cancel(CancelReason.CUSTOMER_CANCELLED, Actor.CUSTOMER, customerId, now);
    }

    private void cancel(CancelReason reason, Actor actor, UUID changedBy, Instant now) {
        transition(OrderStatus.CANCELLED, actor, changedBy, now);
        this.cancelReason = reason;
    }

    private void transition(OrderStatus next, Actor actor, UUID changedBy, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidOrderTransitionException(status, next);
        }
        history.add(new OrderStatusHistory(status, next, actor, changedBy, now));
        this.status = next;
        this.updatedAt = now;
    }

    public boolean belongsToCustomer(UUID userId) {
        return customerId.equals(userId);
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public CancelReason getCancelReason() {
        return cancelReason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<OrderStatusHistory> getHistory() {
        return List.copyOf(history);
    }
}
