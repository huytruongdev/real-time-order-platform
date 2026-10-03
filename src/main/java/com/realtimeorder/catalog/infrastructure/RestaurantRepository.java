package com.realtimeorder.catalog.infrastructure;

import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Page<Restaurant> findByStatus(CatalogStatus status, Pageable pageable);

    boolean existsByIdAndStatus(UUID id, CatalogStatus status);

    boolean existsByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @Query("select r.id from Restaurant r where r.ownerUserId = :ownerUserId")
    List<UUID> findIdsByOwnerUserId(@Param("ownerUserId") UUID ownerUserId);
}
