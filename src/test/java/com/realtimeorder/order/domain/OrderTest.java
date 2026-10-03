package com.realtimeorder.order.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");
    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID RESTAURANT_USER = UUID.randomUUID();

    @Test
    void placeComputesTotalFromSnapshotAndRecordsHistory() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        // 2 × 55 000 + 1 × 45 000
        assertThat(order.getTotalAmount()).isEqualByComparingTo("155000");
        assertThat(order.getCancelReason()).isNull();
        assertThat(order.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getOldStatus()).isNull();
            assertThat(h.getNewStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(h.getActor()).isEqualTo(Actor.CUSTOMER);
            assertThat(h.getChangedBy()).isEqualTo(CUSTOMER);
        });
    }

    @Test
    void orderMustHaveItems() {
        assertThatThrownBy(() -> Order.place(UUID.randomUUID(), CUSTOMER, UUID.randomUUID(), List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restaurantConfirmsCreatedOrder() {
        Order order = newOrder();

        order.confirm(RESTAURANT_USER, NOW.plusSeconds(1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getHistory()).last().satisfies(h -> {
            assertThat(h.getOldStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(h.getActor()).isEqualTo(Actor.RESTAURANT);
            assertThat(h.getChangedBy()).isEqualTo(RESTAURANT_USER);
        });
    }

    @Test
    void restaurantRejectsOnlyCreatedOrder() {
        Order created = newOrder();
        created.reject(RESTAURANT_USER, NOW);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(created.getCancelReason()).isEqualTo(CancelReason.RESTAURANT_REJECTED);

        Order confirmed = newOrder();
        confirmed.confirm(RESTAURANT_USER, NOW);
        assertThatThrownBy(() -> confirmed.reject(RESTAURANT_USER, NOW))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void customerCancelsCreatedOrConfirmedOrder() {
        Order created = newOrder();
        created.cancelByCustomer(CUSTOMER, NOW);
        assertThat(created.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(created.getCancelReason()).isEqualTo(CancelReason.CUSTOMER_CANCELLED);

        Order confirmed = newOrder();
        confirmed.confirm(RESTAURANT_USER, NOW);
        confirmed.cancelByCustomer(CUSTOMER, NOW);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(confirmed.getHistory()).extracting(OrderStatusHistory::getNewStatus)
                .containsExactly(OrderStatus.CREATED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED);
    }

    @Test
    void cancelledOrderIsTerminal() {
        Order order = newOrder();
        order.cancelByCustomer(CUSTOMER, NOW);

        assertThatThrownBy(() -> order.cancelByCustomer(CUSTOMER, NOW))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThatThrownBy(() -> order.confirm(RESTAURANT_USER, NOW))
                .isInstanceOf(InvalidOrderTransitionException.class)
                .hasMessageContaining("CANCELLED");
        assertThat(order.getHistory()).hasSize(2);
    }

    private static Order newOrder() {
        return Order.place(UUID.randomUUID(), CUSTOMER, UUID.randomUUID(), List.of(
                OrderItem.of(UUID.randomUUID(), "Phở bò", new BigDecimal("55000.00"), 2),
                OrderItem.of(UUID.randomUUID(), "Bún chả", new BigDecimal("45000.00"), 1)), NOW);
    }
}
