package com.realtimeorder.order.api;

import com.realtimeorder.catalog.application.ReservationLine;
import com.realtimeorder.order.api.OrderDtos.OrderResponse;
import com.realtimeorder.order.api.OrderDtos.PlaceOrderRequest;
import com.realtimeorder.order.application.OrderService;
import com.realtimeorder.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Order của Customer. Chỉ role CUSTOMER (enforce trong {@code SecurityConfig}). */
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderService orderService;

    OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    OrderResponse place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        var lines = request.items().stream()
                .map(item -> new ReservationLine(item.productId(), item.quantity()))
                .toList();
        return OrderResponse.from(orderService.placeOrder(userId(jwt), request.restaurantId(), lines));
    }

    /** Endpoint client dùng để lấy state mới nhất khi WebSocket reconnect (ADR-004). */
    @GetMapping("/{id}")
    OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(orderService.getOrder(userId(jwt), id));
    }

    @GetMapping
    PageResponse<OrderResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(orderService.listOrders(userId(jwt), page, size), OrderResponse::from);
    }

    @PostMapping("/{id}/cancel")
    OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return OrderResponse.from(orderService.cancel(userId(jwt), id));
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
