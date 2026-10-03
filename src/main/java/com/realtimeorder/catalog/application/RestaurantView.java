package com.realtimeorder.catalog.application;

import com.realtimeorder.catalog.domain.Restaurant;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin restaurant public ra ngoài tầng application.
 * {@code status} là String để module khác không phải phụ thuộc vào package domain của Catalog.
 */
public record RestaurantView(
        UUID id,
        UUID ownerUserId,
        String name,
        String address,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    static RestaurantView from(Restaurant restaurant) {
        return new RestaurantView(
                restaurant.getId(),
                restaurant.getOwnerUserId(),
                restaurant.getName(),
                restaurant.getAddress(),
                restaurant.getStatus().name(),
                restaurant.getVersion(),
                restaurant.getCreatedAt(),
                restaurant.getUpdatedAt());
    }
}
