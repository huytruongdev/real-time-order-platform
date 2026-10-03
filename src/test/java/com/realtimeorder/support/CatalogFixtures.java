package com.realtimeorder.support;

import com.realtimeorder.catalog.application.CatalogAdminService;
import com.realtimeorder.user.application.UserAdminService;
import com.realtimeorder.user.application.UserProfile;
import com.realtimeorder.user.domain.Role;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Dựng dữ liệu catalog qua application service (không qua HTTP) cho test của module khác.
 */
public final class CatalogFixtures {

    private final UserAdminService userAdminService;
    private final CatalogAdminService catalogAdminService;

    public CatalogFixtures(UserAdminService userAdminService, CatalogAdminService catalogAdminService) {
        this.userAdminService = userAdminService;
        this.catalogAdminService = catalogAdminService;
    }

    /** Restaurant mới cùng owner mới; owner đăng nhập bằng {@link TestAuth#PASSWORD}. */
    public RestaurantFixture newRestaurant() {
        UserProfile owner = userAdminService.createUser(
                TestAuth.uniqueEmail("restaurant"), TestAuth.PASSWORD, "Owner", Role.RESTAURANT);
        UUID restaurantId = catalogAdminService.createRestaurant(owner.id(), "Quán " + UUID.randomUUID(), "1 Hàng Bạc").id();
        return new RestaurantFixture(restaurantId, owner);
    }

    public UUID newProduct(UUID restaurantId, String name, String price, int stock) {
        return catalogAdminService.createProduct(restaurantId, name, null, new BigDecimal(price), stock).id();
    }

    public record RestaurantFixture(UUID id, UserProfile owner) {
    }
}
