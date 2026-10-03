package com.realtimeorder.catalog.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Product kèm inventory (ADR-029, ADR-035).
 *
 * Hai nhóm dữ liệu được ghi theo hai cách khác nhau:
 * - Thông tin product (name, description, price, status): ghi qua entity, bảo vệ bằng {@code @Version}.
 * - Stock (available/reserved): CHỈ ghi bằng atomic UPDATE trong {@code ProductRepository}.
 *
 * Cột stock được đánh dấu {@code updatable = false}. Nếu không, khi Admin sửa giá, Hibernate sẽ
 * UPDATE toàn bộ cột, ghi đè available_stock bằng giá trị cũ đã đọc lúc đầu transaction và làm
 * mất các thay đổi stock xảy ra trong lúc đó (lost update).
 */
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    /** Restaurant cùng module, tham chiếu bằng id thay vì {@code @ManyToOne} để tránh lazy loading ngầm. */
    @Column(name = "restaurant_id", nullable = false, updatable = false)
    private UUID restaurantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "available_stock", nullable = false, updatable = false)
    private int availableStock;

    @Column(name = "reserved_stock", nullable = false, updatable = false)
    private int reservedStock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // JPA
    }

    private Product(UUID restaurantId, String name, String description, BigDecimal price,
                    int initialStock, Instant now) {
        if (initialStock < 0) {
            throw new IllegalArgumentException("initialStock không được âm");
        }
        this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId");
        this.name = requireText(name);
        this.description = normalizeDescription(description);
        this.price = requirePositive(price);
        this.availableStock = initialStock;
        this.reservedStock = 0;
        this.status = CatalogStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Product mới luôn ACTIVE, chưa có reservation. */
    public static Product create(UUID restaurantId, String name, String description, BigDecimal price,
                                 int initialStock, Instant now) {
        return new Product(restaurantId, name, description, price, initialStock, now);
    }

    /** Sửa thông tin product. Không đụng tới stock. */
    public void update(String name, String description, BigDecimal price, CatalogStatus status, Instant now) {
        this.name = requireText(name);
        this.description = normalizeDescription(description);
        this.price = requirePositive(price);
        this.status = Objects.requireNonNull(status, "status");
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == CatalogStatus.ACTIVE;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("name không được để trống");
        }
        return value.trim();
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private static BigDecimal requirePositive(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("price phải lớn hơn 0");
        }
        return price;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public CatalogStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
