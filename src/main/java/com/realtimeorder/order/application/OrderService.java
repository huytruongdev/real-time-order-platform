package com.realtimeorder.order.application;

import com.realtimeorder.catalog.application.InventoryService;
import com.realtimeorder.catalog.application.ReservationLine;
import com.realtimeorder.catalog.application.ReservedItem;
import com.realtimeorder.order.application.OrderExceptions.DuplicateOrderItemException;
import com.realtimeorder.order.application.OrderExceptions.OrderNotFoundException;
import com.realtimeorder.order.domain.Order;
import com.realtimeorder.order.domain.OrderItem;
import com.realtimeorder.order.infrastructure.OrderRepository;
import com.realtimeorder.shared.id.IdGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Thao tác của Customer với Order.
 *
 * Phase 3 là synchronous: Order gọi thẳng {@link InventoryService} của Catalog trong cùng
 * transaction. Thay đổi order và thay đổi stock cùng commit hoặc cùng rollback.
 */
@Service
public class OrderService {

    /** Mới nhất trước; id là tiêu chí phụ để thứ tự xác định khi phân trang. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, InventoryService inventoryService, Clock clock) {
        this.orderRepository = orderRepository;
        this.inventoryService = inventoryService;
        this.clock = clock;
    }

    /**
     * Tạo order và reserve inventory (ADR-023).
     *
     * Thứ tự: sinh orderId → reserve (Catalog kiểm tra restaurant/product và lấy snapshot giá) →
     * tạo Order từ snapshot. Không đủ stock thì exception rollback toàn bộ, không có order nào được tạo.
     */
    @Transactional
    public OrderView placeOrder(UUID customerId, UUID restaurantId, List<ReservationLine> lines) {
        Set<UUID> productIds = new HashSet<>();
        for (ReservationLine line : lines) {
            if (!productIds.add(line.productId())) {
                throw new DuplicateOrderItemException();
            }
        }

        UUID orderId = IdGenerator.newId();
        List<ReservedItem> reserved = inventoryService.reserve(orderId, restaurantId, lines);

        List<OrderItem> items = reserved.stream()
                .map(item -> OrderItem.of(item.productId(), item.productName(), item.unitPrice(), item.quantity()))
                .toList();
        Order order = Order.place(orderId, customerId, restaurantId, items, clock.instant());
        return OrderView.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderView getOrder(UUID customerId, UUID orderId) {
        return OrderView.from(loadOwnOrder(customerId, orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderView> listOrders(UUID customerId, int page, int size) {
        return orderRepository.findByCustomerId(customerId, PageRequest.of(page, size, NEWEST_FIRST))
                .map(OrderView::from);
    }

    /**
     * Customer cancel (CREATED, CONFIRMED) và release reservation trong cùng transaction.
     *
     * Phase 3 chưa có Payment nên không có refund. Phase Payment sẽ bổ sung:
     * nếu payment SUCCESS thì Payment → REFUNDED (ADR-024).
     *
     * Đồng thời với Restaurant confirm: cả hai cùng đọc order version N; bên commit sau
     * nhận {@code UPDATE ... WHERE version = N} = 0 row → 409, và rollback cả phần release stock.
     */
    @Transactional
    public OrderView cancel(UUID customerId, UUID orderId) {
        Order order = loadOwnOrder(customerId, orderId);
        Instant now = clock.instant();
        order.cancelByCustomer(customerId, now);
        // Flush trước để kiểm tra version ngay, trước khi động vào stock.
        orderRepository.saveAndFlush(order);
        inventoryService.release(orderId);
        return OrderView.from(order);
    }

    private Order loadOwnOrder(UUID customerId, UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.belongsToCustomer(customerId))
                .orElseThrow(OrderNotFoundException::new);
    }
}
