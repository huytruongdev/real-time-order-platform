package com.realtimeorder.catalog.api;

import com.realtimeorder.catalog.api.CatalogDtos.ProductResponse;
import com.realtimeorder.catalog.api.CatalogDtos.RestaurantResponse;
import com.realtimeorder.catalog.application.CatalogQueryService;
import com.realtimeorder.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Catalog public, không cần đăng nhập (ADR-035). Chỉ trả về restaurant/product ACTIVE. */
@RestController
@RequestMapping("/api/v1/restaurants")
class RestaurantController {

    private final CatalogQueryService catalogQueryService;

    RestaurantController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping
    PageResponse<RestaurantResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(catalogQueryService.listActiveRestaurants(page, size), RestaurantResponse::from);
    }

    @GetMapping("/{id}")
    RestaurantResponse get(@PathVariable UUID id) {
        return RestaurantResponse.from(catalogQueryService.getActiveRestaurant(id));
    }

    @GetMapping("/{id}/products")
    PageResponse<ProductResponse> listProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(catalogQueryService.listActiveProducts(id, page, size), ProductResponse::from);
    }
}
