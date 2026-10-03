package com.realtimeorder.order.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Một lần đổi trạng thái của Order. Chỉ ghi thêm, không sửa. */
@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", updatable = false, length = 20)
    private OrderStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, updatable = false, length = 20)
    private OrderStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false, updatable = false, length = 20)
    private Actor actor;

    @Column(name = "changed_by", updatable = false)
    private UUID changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrderStatusHistory() {
        // JPA
    }

    OrderStatusHistory(OrderStatus oldStatus, OrderStatus newStatus, Actor actor, UUID changedBy, Instant now) {
        if ((actor == Actor.SYSTEM) != (changedBy == null)) {
            throw new IllegalArgumentException("changedBy phải null khi và chỉ khi actor = SYSTEM");
        }
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.actor = actor;
        this.changedBy = changedBy;
        this.createdAt = now;
    }

    public OrderStatus getOldStatus() {
        return oldStatus;
    }

    public OrderStatus getNewStatus() {
        return newStatus;
    }

    public Actor getActor() {
        return actor;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
