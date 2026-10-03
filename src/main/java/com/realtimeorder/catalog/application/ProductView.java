package com.realtimeorder.catalog.application;

import com.realtimeorder.catalog.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin product public ra ngoài tầng application.
 * {@code status} là String để module khác không phải phụ thuộc vào package domain của Catalog.
 */
public record ProductView(
        UUID id,
        UUID restaurantId,
        String name,
        String description,
        BigDecimal price,
        int availableStock,
        int reservedStock,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static ProductView from(Product product) {
        return new ProductView(
                product.getId(),
                product.getRestaurantId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getAvailableStock(),
                product.getReservedStock(),
                product.getStatus().name(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
