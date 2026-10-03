package com.realtimeorder.catalog.infrastructure;

import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByRestaurantId(UUID restaurantId, Pageable pageable);

    Page<Product> findByRestaurantIdAndStatus(UUID restaurantId, CatalogStatus status, Pageable pageable);

    /**
     * Cộng {@code delta} (có thể âm) vào available_stock bằng một câu UPDATE atomic.
     *
     * Không làm theo kiểu "đọc stock → tính trong Java → ghi lại": hai transaction cùng đọc 10,
     * cùng ghi 10 + 1 = 11, và một lần cộng bị mất. Ở đây PostgreSQL khoá row trong lúc UPDATE,
     * nên các UPDATE đồng thời trên cùng product được xếp hàng và mỗi câu thấy giá trị mới nhất.
     *
     * Điều kiện {@code available_stock + :delta >= 0} nằm trong WHERE: nếu không đủ stock thì
     * 0 row được cập nhật, thay vì vi phạm CHECK constraint.
     *
     * Không tăng {@code version}: version chỉ bảo vệ thông tin product (xem {@link Product}).
     *
     * @return số row được cập nhật: 1 nếu thành công, 0 nếu product không tồn tại hoặc không đủ stock
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE products
               SET available_stock = available_stock + :delta,
                   updated_at = :now
             WHERE id = :id
               AND available_stock + :delta >= 0
            """, nativeQuery = true)
    int adjustAvailableStock(@Param("id") UUID id, @Param("delta") int delta, @Param("now") Instant now);
}
