package com.realtimeorder.order.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Một dòng của Order, với snapshot tên và giá tại thời điểm đặt hàng. Không thay đổi sau khi tạo. */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_name", nullable = false, updatable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "subtotal", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    protected OrderItem() {
        // JPA
    }

    private OrderItem(UUID productId, String productName, BigDecimal unitPrice, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải lớn hơn 0");
        }
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new IllegalArgumentException("unitPrice phải lớn hơn 0");
        }
        this.productId = Objects.requireNonNull(productId, "productId");
        this.productName = Objects.requireNonNull(productName, "productName");
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public static OrderItem of(UUID productId, String productName, BigDecimal unitPrice, int quantity) {
        return new OrderItem(productId, productName, unitPrice, quantity);
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
