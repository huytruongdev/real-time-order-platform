package com.realtimeorder.order;

import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.catalog.application.CatalogAdminService;
import com.realtimeorder.catalog.application.CatalogExceptions.InsufficientStockException;
import com.realtimeorder.catalog.application.ReservationLine;
import com.realtimeorder.order.application.OrderService;
import com.realtimeorder.order.application.OrderView;
import com.realtimeorder.order.application.RestaurantOrderService;
import com.realtimeorder.shared.error.BusinessException;
import com.realtimeorder.support.CatalogFixtures;
import com.realtimeorder.support.CatalogFixtures.RestaurantFixture;
import com.realtimeorder.user.application.UserAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Các race condition của Phase 3 với PostgreSQL thật (requirements mục 10).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderConcurrencyTest {

    private static final int CUSTOMERS = 20;

    @Autowired
    OrderService orderService;
    @Autowired
    RestaurantOrderService restaurantOrderService;
    @Autowired
    UserAdminService userAdminService;
    @Autowired
    CatalogAdminService catalogAdminService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    CatalogFixtures fixtures;
    RestaurantFixture restaurant;

    @BeforeEach
    void setUp() {
        fixtures = new CatalogFixtures(userAdminService, catalogAdminService);
        restaurant = fixtures.newRestaurant();
    }

    /** "Hai user cùng mua sản phẩm có số lượng giới hạn": 20 customer tranh 5 phần. */
    @Test
    void limitedStockIsNeverOversold() throws Exception {
        int stock = 5;
        UUID product = fixtures.newProduct(restaurant.id(), "Món hot", "30000", stock);

        List<Outcome> outcomes = runConcurrently(CUSTOMERS, i ->
                orderService.placeOrder(UUID.randomUUID(), restaurant.id(), List.of(new ReservationLine(product, 1))));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(stock);
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
                .allSatisfy(o -> assertThat(o.error()).isInstanceOf(InsufficientStockException.class));
        assertStock(product, 0, stock);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from orders where restaurant_id = ?", Integer.class, restaurant.id())).isEqualTo(stock);
    }

    /**
     * Hai order cùng mua hai món theo thứ tự ngược nhau (P1, P2) và (P2, P1). Nếu lock theo thứ tự
     * trong request, transaction có thể chờ nhau thành vòng (deadlock). InventoryService lock theo
     * productId nên mọi order đều thành công.
     */
    @Test
    void opposingProductOrderDoesNotDeadlock() throws Exception {
        UUID p1 = fixtures.newProduct(restaurant.id(), "Món 1", "10000", 1000);
        UUID p2 = fixtures.newProduct(restaurant.id(), "Món 2", "10000", 1000);

        List<Outcome> outcomes = runConcurrently(CUSTOMERS, i -> orderService.placeOrder(
                UUID.randomUUID(), restaurant.id(), i % 2 == 0
                        ? List.of(new ReservationLine(p1, 1), new ReservationLine(p2, 1))
                        : List.of(new ReservationLine(p2, 1), new ReservationLine(p1, 1))));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertStock(p1, 1000 - CUSTOMERS, CUSTOMERS);
        assertStock(p2, 1000 - CUSTOMERS, CUSTOMERS);
    }

    /**
     * "Customer cancel và Restaurant confirm xảy ra đồng thời": đúng một bên thắng, và stock luôn
     * khớp với trạng thái cuối cùng của order.
     */
    @RepeatedTest(5)
    void cancelAndConfirmRaceHasExactlyOneWinner() throws Exception {
        UUID product = fixtures.newProduct(restaurant.id(), "Phở", "50000", 10);
        UUID customerId = UUID.randomUUID();
        OrderView order = orderService.placeOrder(customerId, restaurant.id(), List.of(new ReservationLine(product, 3)));
        UUID owner = restaurant.owner().id();

        List<Outcome> outcomes = runConcurrently(2, i -> i == 0
                ? orderService.cancel(customerId, order.id())
                : restaurantOrderService.confirm(owner, order.id()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(o -> !o.succeeded()).singleElement()
                .satisfies(o -> assertThat(o.error()).isInstanceOfAny(
                        // Bên thua đọc order trước khi bên thắng commit → version không khớp.
                        OptimisticLockingFailureException.class,
                        // Bên thua đọc order sau khi bên thắng commit → thấy trạng thái mới.
                        BusinessException.class));

        String status = orderService.getOrder(customerId, order.id()).status();
        if (status.equals("CANCELLED")) {
            assertStock(product, 10, 0);
        } else {
            assertThat(status).isEqualTo("CONFIRMED");
            assertStock(product, 7, 3);
        }
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from order_status_history where order_id = ?", Integer.class, order.id()))
                .isEqualTo(2);
    }

    /** Customer bấm cancel hai lần cùng lúc: stock chỉ được trả một lần. */
    @Test
    void doubleCancelReleasesStockOnce() throws Exception {
        UUID product = fixtures.newProduct(restaurant.id(), "Phở", "50000", 10);
        UUID customerId = UUID.randomUUID();
        OrderView order = orderService.placeOrder(customerId, restaurant.id(), List.of(new ReservationLine(product, 4)));

        List<Outcome> outcomes = runConcurrently(2, i -> orderService.cancel(customerId, order.id()));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertStock(product, 10, 0);
    }

    private void assertStock(UUID productId, int available, int reserved) {
        var row = jdbcTemplate.queryForMap(
                "select available_stock, reserved_stock from products where id = ?", productId);
        assertThat(row.get("available_stock")).as("available_stock").isEqualTo(available);
        assertThat(row.get("reserved_stock")).as("reserved_stock").isEqualTo(reserved);
    }

    @FunctionalInterface
    private interface IndexedTask {
        Object run(int index) throws Exception;
    }

    private static List<Outcome> runConcurrently(int threads, IndexedTask task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Object> callable = () -> {
                    start.await();
                    return task.run(index);
                };
                futures.add(executor.submit(callable));
            }
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    outcomes.add(new Outcome(null));
                } catch (ExecutionException e) {
                    outcomes.add(new Outcome(e.getCause()));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private record Outcome(Throwable error) {
        boolean succeeded() {
            return error == null;
        }
    }
}
