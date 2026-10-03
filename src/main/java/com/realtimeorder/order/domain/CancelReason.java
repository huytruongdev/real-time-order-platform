package com.realtimeorder.order.domain;

/** Lý do Order bị CANCELLED. PAYMENT_EXPIRED dùng ở phase Payment (ADR-033). */
public enum CancelReason {
    CUSTOMER_CANCELLED,
    RESTAURANT_REJECTED,
    PAYMENT_EXPIRED
}
