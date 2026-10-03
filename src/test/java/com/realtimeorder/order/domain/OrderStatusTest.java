package com.realtimeorder.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.realtimeorder.order.domain.OrderStatus.CANCELLED;
import static com.realtimeorder.order.domain.OrderStatus.CONFIRMED;
import static com.realtimeorder.order.domain.OrderStatus.CREATED;
import static com.realtimeorder.order.domain.OrderStatus.DELIVERED;
import static com.realtimeorder.order.domain.OrderStatus.DELIVERING;
import static com.realtimeorder.order.domain.OrderStatus.DRIVER_ASSIGNED;
import static com.realtimeorder.order.domain.OrderStatus.PICKING_UP;
import static com.realtimeorder.order.domain.OrderStatus.PREPARING;
import static com.realtimeorder.order.domain.OrderStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toàn bộ ma trận transition so với bảng trong requirements mục 3.2.
 */
class OrderStatusTest {

    /** Nguồn sự thật cho test, chép từ requirements mục 3.2. */
    private static final Map<OrderStatus, Set<OrderStatus>> VALID = Map.of(
            CREATED, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(PREPARING, CANCELLED),
            PREPARING, Set.of(READY),
            READY, Set.of(DRIVER_ASSIGNED),
            DRIVER_ASSIGNED, Set.of(PICKING_UP),
            PICKING_UP, Set.of(DELIVERING),
            DELIVERING, Set.of(DELIVERED),
            DELIVERED, Set.of(),
            CANCELLED, Set.of());

    static Stream<Arguments> allPairs() {
        return Arrays.stream(OrderStatus.values())
                .flatMap(from -> Arrays.stream(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("allPairs")
    void transitionMatrixMatchesRequirements(OrderStatus from, OrderStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(VALID.get(from).contains(to));
    }

    @Test
    void driverAssignedCannotGoBackToReady() {
        assertThat(DRIVER_ASSIGNED.canTransitionTo(READY)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED"})
    void terminalStatesHaveNoTransition(OrderStatus status) {
        assertThat(status.isTerminal()).isTrue();
        assertThat(status.allowedNext()).isEmpty();
    }

    @Test
    void orderHasNoPaidOrFailedStatus() {
        assertThat(Arrays.stream(OrderStatus.values()).map(Enum::name))
                .doesNotContain("PAID", "FAILED", "PAYMENT_EXPIRED");
    }
}
