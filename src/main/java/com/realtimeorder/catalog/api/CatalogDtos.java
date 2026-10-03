package com.realtimeorder.catalog.api;

import com.realtimeorder.catalog.application.ProductView;
import com.realtimeorder.catalog.application.RestaurantView;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Request/response của Catalog API.
 *
 * Response public (Customer) không chứa owner, reserved stock, version: đó là dữ liệu nội bộ.
 * Response admin chứa {@code version} để client gửi lại khi sửa (optimistic locking).
 */
final class CatalogDtos {

    static final int MAX_STOCK = 1_000_000;
    static final String STATUS_PATTERN = "ACTIVE|INACTIVE";
    static final String STATUS_MESSAGE = "phải là ACTIVE hoặc INACTIVE";

    private CatalogDtos() {
    }

    // ---------- public ----------

    record RestaurantResponse(UUID id, String name, String address) {

        static RestaurantResponse from(RestaurantView view) {
            return new RestaurantResponse(view.id(), view.name(), view.address());
        }
    }

    record ProductResponse(UUID id, UUID restaurantId, String name, String description,
                           BigDecimal price, int availableStock) {

        static ProductResponse from(ProductView view) {
            return new ProductResponse(view.id(), view.restaurantId(), view.name(), view.description(),
                    view.price(), view.availableStock());
        }
    }

    // ---------- admin ----------

    record CreateRestaurantRequest(
            @NotNull UUID ownerUserId,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address) {
    }

    record UpdateRestaurantRequest(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 500) String address,
            @NotBlank @Pattern(regexp = STATUS_PATTERN, message = STATUS_MESSAGE) String status,
            @NotNull Long version) {
    }

    record CreateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            // NUMERIC(12, 2): tối đa 10 chữ số phần nguyên, 2 chữ số thập phân.
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull @Min(0) @Max(MAX_STOCK) Integer initialStock) {
    }

    record UpdateProductRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotBlank @Pattern(regexp = STATUS_PATTERN, message = STATUS_MESSAGE) String status,
            @NotNull Long version) {
    }

    record StockAdjustmentRequest(
            @NotNull @Min(-MAX_STOCK) @Max(MAX_STOCK) Integer delta) {
    }

    record AdminRestaurantResponse(UUID id, UUID ownerUserId, String name, String address, String status,
                                   long version, Instant createdAt, Instant updatedAt) {

        static AdminRestaurantResponse from(RestaurantView view) {
            return new AdminRestaurantResponse(view.id(), view.ownerUserId(), view.name(), view.address(),
                    view.status(), view.version(), view.createdAt(), view.updatedAt());
        }
    }

    record AdminProductResponse(UUID id, UUID restaurantId, String name, String description, BigDecimal price,
                                int availableStock, int reservedStock, String status, long version,
                                Instant createdAt, Instant updatedAt) {

        static AdminProductResponse from(ProductView view) {
            return new AdminProductResponse(view.id(), view.restaurantId(), view.name(), view.description(),
                    view.price(), view.availableStock(), view.reservedStock(), view.status(), view.version(),
                    view.createdAt(), view.updatedAt());
        }
    }
}
