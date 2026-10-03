package com.realtimeorder.order.api;

import com.realtimeorder.order.application.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Request/response của Order API.
 *
 * Request chỉ có productId và quantity. Giá luôn do server lấy từ Catalog: không bao giờ tin giá
 * do client gửi lên.
 */
final class OrderDtos {

    static final int MAX_ITEMS = 50;
    static final int MAX_QUANTITY = 100;

    private OrderDtos() {
    }

    record PlaceOrderRequest(
            @NotNull UUID restaurantId,
            @NotEmpty @Size(max = MAX_ITEMS) List<@NotNull @Valid OrderLineRequest> items) {
    }

    record OrderLineRequest(
            @NotNull UUID productId,
            @NotNull @Min(1) @Max(MAX_QUANTITY) Integer quantity) {
    }

    record OrderResponse(
            UUID id,
            UUID customerId,
            UUID restaurantId,
            String status,
            String cancelReason,
            BigDecimal totalAmount,
            List<OrderItemResponse> items,
            Instant createdAt,
            Instant updatedAt) {

        static OrderResponse from(OrderView view) {
            return new OrderResponse(
                    view.id(),
                    view.customerId(),
                    view.restaurantId(),
                    view.status(),
                    view.cancelReason(),
                    view.totalAmount(),
                    view.items().stream().map(OrderItemResponse::from).toList(),
                    view.createdAt(),
                    view.updatedAt());
        }
    }

    record OrderItemResponse(UUID productId, String productName, BigDecimal unitPrice, int quantity,
                             BigDecimal subtotal) {

        static OrderItemResponse from(OrderView.Item item) {
            return new OrderItemResponse(item.productId(), item.productName(), item.unitPrice(),
                    item.quantity(), item.subtotal());
        }
    }
}
