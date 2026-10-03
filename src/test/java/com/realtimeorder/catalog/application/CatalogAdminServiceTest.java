package com.realtimeorder.catalog.application;

import com.realtimeorder.catalog.application.CatalogExceptions.InsufficientStockException;
import com.realtimeorder.catalog.application.CatalogExceptions.InvalidRestaurantOwnerException;
import com.realtimeorder.catalog.application.CatalogExceptions.InvalidStockAdjustmentException;
import com.realtimeorder.catalog.application.CatalogExceptions.ProductNotFoundException;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.Restaurant;
import com.realtimeorder.catalog.infrastructure.ProductRepository;
import com.realtimeorder.catalog.infrastructure.RestaurantRepository;
import com.realtimeorder.shared.error.StaleVersionException;
import com.realtimeorder.user.application.UserProfile;
import com.realtimeorder.user.application.UserQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    @Mock
    RestaurantRepository restaurantRepository;
    @Mock
    ProductRepository productRepository;
    @Mock
    UserQuery userQuery;

    CatalogAdminService service;

    @BeforeEach
    void setUp() {
        service = new CatalogAdminService(restaurantRepository, productRepository, userQuery,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void ownerMustExist() {
        UUID owner = UUID.randomUUID();
        when(userQuery.findById(owner)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRestaurant(owner, "Quán", "Địa chỉ"))
                .isInstanceOf(InvalidRestaurantOwnerException.class);
        verify(restaurantRepository, never()).saveAndFlush(any());
    }

    @Test
    void ownerMustHaveRestaurantRole() {
        UUID owner = UUID.randomUUID();
        when(userQuery.findById(owner))
                .thenReturn(Optional.of(new UserProfile(owner, "c@example.com", "C", "CUSTOMER", NOW)));

        assertThatThrownBy(() -> service.createRestaurant(owner, "Quán", "Địa chỉ"))
                .isInstanceOf(InvalidRestaurantOwnerException.class);
    }

    @Test
    void updateWithStaleVersionIsRejectedBeforeAnyChange() {
        Restaurant restaurant = Restaurant.create(UUID.randomUUID(), "Quán", "Địa chỉ", NOW);
        ReflectionTestUtils.setField(restaurant, "version", 3L);
        when(restaurantRepository.findById(restaurant.getId())).thenReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> service.updateRestaurant(
                restaurant.getId(), "Tên mới", "Địa chỉ", CatalogStatus.ACTIVE, 2L))
                .isInstanceOf(StaleVersionException.class);
        verify(restaurantRepository, never()).saveAndFlush(any());
    }

    @Test
    void zeroStockAdjustmentIsRejected() {
        assertThatThrownBy(() -> service.adjustStock(UUID.randomUUID(), 0))
                .isInstanceOf(InvalidStockAdjustmentException.class);
        verify(productRepository, never()).adjustAvailableStock(any(), anyInt(), any());
    }

    @Test
    void stockAdjustmentOfUnknownProductIsNotFound() {
        UUID productId = UUID.randomUUID();
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThatThrownBy(() -> service.adjustStock(productId, 5))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void stockAdjustmentUpdatingNoRowMeansInsufficientStock() {
        UUID productId = UUID.randomUUID();
        when(productRepository.existsById(productId)).thenReturn(true);
        when(productRepository.adjustAvailableStock(productId, -5, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.adjustStock(productId, -5))
                .isInstanceOf(InsufficientStockException.class);
    }
}
