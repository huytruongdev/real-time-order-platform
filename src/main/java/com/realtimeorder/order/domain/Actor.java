package com.realtimeorder.order.domain;

/** Ai gây ra một lần đổi trạng thái của Order. SYSTEM không có user (changed_by = null). */
public enum Actor {
    CUSTOMER,
    RESTAURANT,
    DRIVER,
    SYSTEM
}
