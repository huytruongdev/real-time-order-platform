package com.realtimeorder.catalog.application;

import java.util.Objects;
import java.util.UUID;

/** Một dòng cần reserve: product nào, bao nhiêu. */
public record ReservationLine(UUID productId, int quantity) {

    public ReservationLine {
        Objects.requireNonNull(productId, "productId");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải lớn hơn 0");
        }
    }
}
