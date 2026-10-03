package com.realtimeorder.order.application;

import com.realtimeorder.order.domain.Order;
import com.realtimeorder.order.domain.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Thông tin order public ra ngoài tầng application.
 * Enum được đổi thành String để module khác không phụ thuộc vào package domain của Order.
 */
public record OrderView(
        UUID id,
        UUID customerId,
        UUID restaurantId,
        String status,
        String cancelReason,
        BigDecimal totalAmount,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(UUID productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {

        static Item from(OrderItem item) {
            return new Item(item.getProductId(), item.getProductName(), item.getUnitPrice(),
                    item.getQuantity(), item.getSubtotal());
        }
    }

    static OrderView from(Order order) {
        return new OrderView(
                order.getId(),
                order.getCustomerId(),
                order.getRestaurantId(),
                order.getStatus().name(),
                order.getCancelReason() == null ? null : order.getCancelReason().name(),
                order.getTotalAmount(),
                order.getItems().stream().map(Item::from).toList(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
