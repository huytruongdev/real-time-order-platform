package com.realtimeorder.catalog.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Số lượng một order đang giữ của một product (ADR-036).
 *
 * Chỉ đi theo một chiều: RESERVED → RELEASED hoặc RESERVED → COMMITTED.
 */
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {
        // JPA
    }

    private InventoryReservation(UUID orderId, UUID productId, int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải lớn hơn 0");
        }
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static InventoryReservation reserve(UUID orderId, UUID productId, int quantity, Instant now) {
        return new InventoryReservation(orderId, productId, quantity, now);
    }

    public void release(Instant now) {
        moveFromReserved(ReservationStatus.RELEASED, now);
    }

    public void commit(Instant now) {
        moveFromReserved(ReservationStatus.COMMITTED, now);
    }

    private void moveFromReserved(ReservationStatus target, Instant now) {
        if (status != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Reservation " + getId() + " đang " + status + ", không thể chuyển sang " + target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}
