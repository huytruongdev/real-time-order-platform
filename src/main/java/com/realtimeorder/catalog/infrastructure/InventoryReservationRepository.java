package com.realtimeorder.catalog.infrastructure;

import com.realtimeorder.catalog.domain.InventoryReservation;
import com.realtimeorder.catalog.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    /**
     * {@code SELECT ... FOR UPDATE} các reservation còn RESERVED của order.
     *
     * Hai lần release/commit đồng thời cho cùng order: lần thứ hai phải chờ lần đầu commit.
     * Sau khi có lock, PostgreSQL kiểm tra lại điều kiện {@code status = RESERVED} trên phiên bản row
     * mới nhất, nên lần thứ hai không thấy row nào và không trả stock thêm lần nữa.
     *
     * Sort theo productId: cùng thứ tự lock với reserve, tránh deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r from InventoryReservation r
             where r.orderId = :orderId and r.status = :status
             order by r.productId
            """)
    List<InventoryReservation> findForUpdate(@Param("orderId") UUID orderId,
                                             @Param("status") ReservationStatus status);

    List<InventoryReservation> findByOrderId(UUID orderId);
}
