package com.realtimeorder.order.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order state machine (requirements mục 3.1, 3.2). Monotonic: không có transition đi lùi.
 *
 * Không có PAID, PAYMENT_EXPIRED, FAILED (ADR-021, ADR-022, ADR-032).
 * Bảng transition ở đây chỉ trả lời "có được đi từ A sang B không". Rule về actor
 * (ví dụ restaurant chỉ reject khi CREATED) nằm trong {@link Order}.
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    PREPARING,
    READY,
    DRIVER_ASSIGNED,
    PICKING_UP,
    DELIVERING,
    DELIVERED,
    CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case CREATED -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PREPARING, CANCELLED);
            case PREPARING -> EnumSet.of(READY);
            case READY -> EnumSet.of(DRIVER_ASSIGNED);
            case DRIVER_ASSIGNED -> EnumSet.of(PICKING_UP);
            case PICKING_UP -> EnumSet.of(DELIVERING);
            case DELIVERING -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
