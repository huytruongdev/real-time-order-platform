package com.realtimeorder.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestaurantTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Test
    void newRestaurantIsActive() {
        UUID owner = UUID.randomUUID();

        Restaurant restaurant = Restaurant.create(owner, " Quán Phở ", " 1 Hàng Bạc ", NOW);

        assertThat(restaurant.isActive()).isTrue();
        assertThat(restaurant.getOwnerUserId()).isEqualTo(owner);
        assertThat(restaurant.getName()).isEqualTo("Quán Phở");
        assertThat(restaurant.getAddress()).isEqualTo("1 Hàng Bạc");
    }

    @Test
    void ownerIsRequired() {
        assertThatThrownBy(() -> Restaurant.create(null, "Quán", "Địa chỉ", NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nameMustNotBeBlank() {
        Restaurant restaurant = Restaurant.create(UUID.randomUUID(), "Quán", "Địa chỉ", NOW);

        assertThatThrownBy(() -> restaurant.update(" ", "Địa chỉ", CatalogStatus.ACTIVE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateCanDeactivate() {
        Restaurant restaurant = Restaurant.create(UUID.randomUUID(), "Quán", "Địa chỉ", NOW);

        restaurant.update("Quán mới", "Địa chỉ mới", CatalogStatus.INACTIVE, NOW.plusSeconds(1));

        assertThat(restaurant.isActive()).isFalse();
        assertThat(restaurant.getName()).isEqualTo("Quán mới");
        assertThat(restaurant.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }
}
