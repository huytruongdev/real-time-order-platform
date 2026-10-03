package com.realtimeorder.catalog.infrastructure;

import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Page<Restaurant> findByStatus(CatalogStatus status, Pageable pageable);

    boolean existsByIdAndStatus(UUID id, CatalogStatus status);
}
