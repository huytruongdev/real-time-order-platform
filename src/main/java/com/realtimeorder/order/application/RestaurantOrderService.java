package com.realtimeorder.order.application;

import com.realtimeorder.catalog.application.CatalogQueryService;
import com.realtimeorder.catalog.application.InventoryService;
import com.realtimeorder.order.application.OrderExceptions.OrderNotFoundException;
import com.realtimeorder.order.domain.Order;
import com.realtimeorder.order.domain.OrderStatus;
import com.realtimeorder.order.infrastructure.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Thao tác của Restaurant user với order của các restaurant mình sở hữu (ADR-035: owner_user_id).
 */
@Service
public class RestaurantOrderService {

    private final OrderRepository orderRepository;
    private final CatalogQueryService catalogQueryService;
    private final InventoryService inventoryService;
    private final Clock clock;

    public RestaurantOrderService(OrderRepository orderRepository,
                                  CatalogQueryService catalogQueryService,
                                  InventoryService inventoryService,
                                  Clock clock) {
        this.orderRepository = orderRepository;
        this.catalogQueryService = catalogQueryService;
        this.inventoryService = inventoryService;
        this.clock = clock;
    }

    /** @param status null để lấy mọi trạng thái */
    @Transactional(readOnly = true)
    public Page<OrderView> listOrders(UUID restaurantUserId, OrderStatus status, int page, int size) {
        List<UUID> restaurantIds = catalogQueryService.findRestaurantIdsOwnedBy(restaurantUserId);
        Pageable pageable = PageRequest.of(page, size, OrderService.NEWEST_FIRST);
        if (restaurantIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<Order> orders = status == null
                ? orderRepository.findByRestaurantIdIn(restaurantIds, pageable)
                : orderRepository.findByRestaurantIdInAndStatus(restaurantIds, status, pageable);
        return orders.map(OrderView::from);
    }

    /** CREATED → CONFIRMED. Reservation vẫn được giữ chờ payment. */
    @Transactional
    public OrderView confirm(UUID restaurantUserId, UUID orderId) {
        Order order = loadOwnedOrder(restaurantUserId, orderId);
        order.confirm(restaurantUserId, clock.instant());
        return OrderView.from(orderRepository.saveAndFlush(order));
    }

    /** CREATED → CANCELLED và release reservation (ADR-036). */
    @Transactional
    public OrderView reject(UUID restaurantUserId, UUID orderId) {
        Order order = loadOwnedOrder(restaurantUserId, orderId);
        order.reject(restaurantUserId, clock.instant());
        orderRepository.saveAndFlush(order);
        inventoryService.release(orderId);
        return OrderView.from(order);
    }

    private Order loadOwnedOrder(UUID restaurantUserId, UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(order -> catalogQueryService.isOwner(order.getRestaurantId(), restaurantUserId))
                .orElseThrow(OrderNotFoundException::new);
    }
}
