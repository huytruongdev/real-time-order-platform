package com.realtimeorder.order.api;

import com.realtimeorder.order.api.OrderDtos.OrderResponse;
import com.realtimeorder.order.application.RestaurantOrderService;
import com.realtimeorder.order.domain.OrderStatus;
import com.realtimeorder.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Order của các restaurant mà Restaurant user đang đăng nhập sở hữu.
 * Chỉ role RESTAURANT (enforce trong {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/api/v1/restaurant/orders")
class RestaurantOrderController {

    private final RestaurantOrderService restaurantOrderService;

    RestaurantOrderController(RestaurantOrderService restaurantOrderService) {
        this.restaurantOrderService = restaurantOrderService;
    }

    /** Ví dụ xem đơn mới: {@code ?status=CREATED}. */
    @GetMapping
    PageResponse<OrderResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(restaurantOrderService.listOrders(userId(jwt), status, page, size),
                OrderResponse::from);
    }

    @PostMapping("/{id}/confirm")
    OrderResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(restaurantOrderService.confirm(userId(jwt), id));
    }

    @PostMapping("/{id}/reject")
    OrderResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(restaurantOrderService.reject(userId(jwt), id));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
