package com.realtimeorder.catalog.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private static final UUID RESTAURANT_ID = UUID.randomUUID();

    @Test
    void newProductIsActiveWithoutReservation() {
        Product product = Product.create(RESTAURANT_ID, "  Phở bò ", "  ", new BigDecimal("55000"), 10, NOW);

        assertThat(product.getStatus()).isEqualTo(CatalogStatus.ACTIVE);
        assertThat(product.getName()).isEqualTo("Phở bò");
        assertThat(product.getDescription()).isNull();
        assertThat(product.getAvailableStock()).isEqualTo(10);
        assertThat(product.getReservedStock()).isZero();
        assertThat(product.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void priceMustBePositive() {
        assertThatThrownBy(() -> Product.create(RESTAURANT_ID, "Phở", null, BigDecimal.ZERO, 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Product.create(RESTAURANT_ID, "Phở", null, new BigDecimal("-1"), 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void initialStockMustNotBeNegative() {
        assertThatThrownBy(() -> Product.create(RESTAURANT_ID, "Phở", null, BigDecimal.TEN, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateChangesInfoButNotStock() {
        Product product = Product.create(RESTAURANT_ID, "Phở", null, BigDecimal.TEN, 10, NOW);
        Instant later = NOW.plusSeconds(60);

        product.update("Phở gà", "Ngon", new BigDecimal("60000"), CatalogStatus.INACTIVE, later);

        assertThat(product.getName()).isEqualTo("Phở gà");
        assertThat(product.getPrice()).isEqualByComparingTo("60000");
        assertThat(product.isActive()).isFalse();
        assertThat(product.getAvailableStock()).isEqualTo(10);
        assertThat(product.getUpdatedAt()).isEqualTo(later);
        assertThat(product.getCreatedAt()).isEqualTo(NOW);
    }
}
