package com.realtimeorder.catalog;

import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.catalog.application.CatalogAdminService;
import com.realtimeorder.catalog.application.CatalogExceptions.InsufficientStockException;
import com.realtimeorder.catalog.application.CatalogExceptions.ProductNotAvailableException;
import com.realtimeorder.catalog.application.CatalogExceptions.RestaurantNotFoundException;
import com.realtimeorder.catalog.application.InventoryService;
import com.realtimeorder.catalog.application.ReservationLine;
import com.realtimeorder.catalog.application.ReservedItem;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.support.CatalogFixtures;
import com.realtimeorder.support.CatalogFixtures.RestaurantFixture;
import com.realtimeorder.user.application.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 3: reserve / release / commit với PostgreSQL thật (ADR-023, ADR-036).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InventoryServiceIntegrationTest {

    @Autowired
    InventoryService inventoryService;
    @Autowired
    CatalogAdminService catalogAdminService;
    @Autowired
    UserAdminService userAdminService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    CatalogFixtures fixtures;
    RestaurantFixture restaurant;

    @BeforeEach
    void setUp() {
        fixtures = new CatalogFixtures(userAdminService, catalogAdminService);
        restaurant = fixtures.newRestaurant();
    }

    @Test
    void reserveMovesStockFromAvailableToReservedAndReturnsSnapshot() {
        UUID pho = fixtures.newProduct(restaurant.id(), "Phở", "55000", 10);
        UUID orderId = UUID.randomUUID();

        List<ReservedItem> items = inventoryService.reserve(orderId, restaurant.id(),
                List.of(new ReservationLine(pho, 3)));

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.productName()).isEqualTo("Phở");
            assertThat(item.unitPrice()).isEqualByComparingTo("55000");
            assertThat(item.quantity()).isEqualTo(3);
        });
        assertStock(pho, 7, 3);
        assertThat(reservationStatuses(orderId)).containsExactly("RESERVED");
    }

    @Test
    void reserveIsAllOrNothing() {
        UUID enough = fixtures.newProduct(restaurant.id(), "Đủ hàng", "10000", 10);
        UUID notEnough = fixtures.newProduct(restaurant.id(), "Thiếu hàng", "10000", 1);
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryService.reserve(orderId, restaurant.id(), List.of(
                new ReservationLine(enough, 5),
                new ReservationLine(notEnough, 2))))
                .isInstanceOf(InsufficientStockException.class);

        // Dòng đã reserve thành công cũng bị rollback.
        assertStock(enough, 10, 0);
        assertStock(notEnough, 1, 0);
        assertThat(reservationStatuses(orderId)).isEmpty();
    }

    @Test
    void reserveRejectsProductOfAnotherRestaurantOrInactiveProduct() {
        UUID otherRestaurantProduct = fixtures.newProduct(fixtures.newRestaurant().id(), "Món quán khác", "10000", 5);
        UUID inactive = fixtures.newProduct(restaurant.id(), "Ngừng bán", "10000", 5);
        var view = catalogAdminService.getProduct(inactive);
        catalogAdminService.updateProduct(inactive, view.name(), null, view.price(), CatalogStatus.INACTIVE, view.version());

        assertThatThrownBy(() -> inventoryService.reserve(UUID.randomUUID(), restaurant.id(),
                List.of(new ReservationLine(otherRestaurantProduct, 1))))
                .isInstanceOf(ProductNotAvailableException.class);
        assertThatThrownBy(() -> inventoryService.reserve(UUID.randomUUID(), restaurant.id(),
                List.of(new ReservationLine(inactive, 1))))
                .isInstanceOf(ProductNotAvailableException.class);
        assertThatThrownBy(() -> inventoryService.reserve(UUID.randomUUID(), restaurant.id(),
                List.of(new ReservationLine(UUID.randomUUID(), 1))))
                .isInstanceOf(ProductNotAvailableException.class);
    }

    @Test
    void reserveRejectsInactiveRestaurant() {
        UUID pho = fixtures.newProduct(restaurant.id(), "Phở", "55000", 10);
        var view = catalogAdminService.getRestaurant(restaurant.id());
        catalogAdminService.updateRestaurant(restaurant.id(), view.name(), view.address(),
                CatalogStatus.INACTIVE, view.version());

        assertThatThrownBy(() -> inventoryService.reserve(UUID.randomUUID(), restaurant.id(),
                List.of(new ReservationLine(pho, 1))))
                .isInstanceOf(RestaurantNotFoundException.class);
        assertStock(pho, 10, 0);
    }

    @Test
    void releaseReturnsStockAndIsIdempotent() {
        UUID pho = fixtures.newProduct(restaurant.id(), "Phở", "55000", 10);
        UUID orderId = UUID.randomUUID();
        inventoryService.reserve(orderId, restaurant.id(), List.of(new ReservationLine(pho, 4)));

        inventoryService.release(orderId);
        inventoryService.release(orderId);

        assertStock(pho, 10, 0);
        assertThat(reservationStatuses(orderId)).containsExactly("RELEASED");
    }

    @Test
    void commitTurnsReservationIntoSoldAndCannotBeReleasedAfterwards() {
        UUID pho = fixtures.newProduct(restaurant.id(), "Phở", "55000", 10);
        UUID orderId = UUID.randomUUID();
        inventoryService.reserve(orderId, restaurant.id(), List.of(new ReservationLine(pho, 4)));

        inventoryService.commit(orderId);
        inventoryService.commit(orderId);
        inventoryService.release(orderId);

        // available giữ nguyên 6, reserved về 0: 4 sản phẩm đã bán.
        assertStock(pho, 6, 0);
        assertThat(reservationStatuses(orderId)).containsExactly("COMMITTED");
    }

    @Test
    void releaseOfUnknownOrderDoesNothing() {
        inventoryService.release(UUID.randomUUID());
    }

    private void assertStock(UUID productId, int available, int reserved) {
        var row = jdbcTemplate.queryForMap(
                "select available_stock, reserved_stock from products where id = ?", productId);
        assertThat(row.get("available_stock")).isEqualTo(available);
        assertThat(row.get("reserved_stock")).isEqualTo(reserved);
    }

    private List<String> reservationStatuses(UUID orderId) {
        return jdbcTemplate.queryForList(
                "select status from inventory_reservations where order_id = ?", String.class, orderId);
    }
}
