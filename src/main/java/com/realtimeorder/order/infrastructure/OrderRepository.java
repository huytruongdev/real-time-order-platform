package com.realtimeorder.order.infrastructure;

import com.realtimeorder.order.domain.Order;
import com.realtimeorder.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByRestaurantIdIn(Collection<UUID> restaurantIds, Pageable pageable);

    Page<Order> findByRestaurantIdInAndStatus(Collection<UUID> restaurantIds, OrderStatus status, Pageable pageable);
}
