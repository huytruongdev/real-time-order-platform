package com.realtimeorder.catalog;

import com.realtimeorder.TestcontainersConfiguration;
import com.realtimeorder.catalog.application.CatalogAdminService;
import com.realtimeorder.catalog.application.CatalogExceptions.InsufficientStockException;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.Product;
import com.realtimeorder.catalog.infrastructure.ProductRepository;
import com.realtimeorder.user.application.UserAdminService;
import com.realtimeorder.user.domain.Role;
import com.realtimeorder.support.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
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
 * Atomic conditional UPDATE dưới tải đồng thời, với PostgreSQL thật.
 *
 * Đây cũng là nền tảng cho reserve stock ở Phase 3: "hai customer cùng mua món cuối cùng".
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StockAdjustmentConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired
    CatalogAdminService catalogAdminService;
    @Autowired
    UserAdminService userAdminService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    TransactionTemplate transactionTemplate;

    /** Nếu dùng "đọc → cộng trong Java → ghi" thì một số lần cộng sẽ bị mất (lost update). */
    @Test
    void concurrentIncrementsAreNotLost() throws Exception {
        UUID productId = newProduct(0);

        List<Throwable> errors = runConcurrently(THREADS, () -> catalogAdminService.adjustStock(productId, 1));

        assertThat(errors).containsOnlyNulls();
        assertThat(catalogAdminService.getProduct(productId).availableStock()).isEqualTo(THREADS);
    }

    /** Stock 5, 20 request cùng trừ 1: đúng 5 request thành công, stock dừng ở 0, không bao giờ âm. */
    @Test
    void concurrentDecrementsNeverOversell() throws Exception {
        int initialStock = 5;
        UUID productId = newProduct(initialStock);

        List<Throwable> errors = runConcurrently(THREADS, () -> catalogAdminService.adjustStock(productId, -1));

        assertThat(errors).filteredOn(error -> error == null).hasSize(initialStock);
        assertThat(errors).filteredOn(error -> error != null)
                .hasSize(THREADS - initialStock)
                .allSatisfy(error -> assertThat(error).isInstanceOf(InsufficientStockException.class));
        assertThat(catalogAdminService.getProduct(productId).availableStock()).isZero();
    }

    /**
     * Lost update giữa "sửa thông tin product" và "thay đổi stock":
     *
     * <pre>
     * T1 (admin sửa giá)            T2 (nhập kho)
     * SELECT product (stock = 5)
     *                               UPDATE stock = stock + 7 → 12, COMMIT
     * UPDATE products SET ...       ← nếu cột stock không phải updatable = false,
     * COMMIT                           Hibernate ghi lại stock = 5 và mất 7 đơn vị vừa nhập
     * </pre>
     */
    @Test
    void productInfoUpdateDoesNotOverwriteConcurrentStockChange() throws Exception {
        UUID productId = newProduct(5);

        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findById(productId).orElseThrow();
            assertThat(product.getAvailableStock()).isEqualTo(5);

            // T2 chạy trên thread khác, có transaction và connection riêng, commit trước T1.
            try {
                assertThat(runConcurrently(1, () -> catalogAdminService.adjustStock(productId, 7)))
                        .containsOnlyNulls();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }

            product.update("Giá mới", null, new BigDecimal("35000"), CatalogStatus.ACTIVE, Instant.now());
        });

        var result = catalogAdminService.getProduct(productId);
        assertThat(result.availableStock()).isEqualTo(12);
        assertThat(result.price()).isEqualByComparingTo("35000");
    }

    private UUID newProduct(int stock) {
        UUID owner = userAdminService.createUser(
                TestAuth.uniqueEmail("restaurant"), TestAuth.PASSWORD, "Owner", Role.RESTAURANT).id();
        UUID restaurantId = catalogAdminService.createRestaurant(owner, "Quán đông khách", "1 Hàng Bạc").id();
        return catalogAdminService.createProduct(restaurantId, "Món hot", null, new BigDecimal("30000"), stock).id();
    }

    /** @return lỗi của từng task, {@code null} nếu task thành công */
    private static List<Throwable> runConcurrently(int threads, Callable<?> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();

            List<Throwable> errors = new ArrayList<>();
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    errors.add(null);
                } catch (ExecutionException e) {
                    errors.add(e.getCause());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
            return errors;
        } finally {
            executor.shutdownNow();
        }
    }
}
