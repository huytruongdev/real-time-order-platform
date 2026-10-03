package com.realtimeorder.catalog.api;

import com.realtimeorder.catalog.api.CatalogDtos.AdminProductResponse;
import com.realtimeorder.catalog.api.CatalogDtos.AdminRestaurantResponse;
import com.realtimeorder.catalog.api.CatalogDtos.CreateProductRequest;
import com.realtimeorder.catalog.api.CatalogDtos.CreateRestaurantRequest;
import com.realtimeorder.catalog.api.CatalogDtos.StockAdjustmentRequest;
import com.realtimeorder.catalog.api.CatalogDtos.UpdateProductRequest;
import com.realtimeorder.catalog.api.CatalogDtos.UpdateRestaurantRequest;
import com.realtimeorder.catalog.application.CatalogAdminService;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Chỉ ADMIN (enforce trong {@code SecurityConfig} cho {@code /api/v1/admin/**}). */
@RestController
@RequestMapping("/api/v1/admin")
class AdminCatalogController {

    private final CatalogAdminService catalogAdminService;

    AdminCatalogController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    // ---------- restaurant ----------

    @PostMapping("/restaurants")
    @ResponseStatus(HttpStatus.CREATED)
    AdminRestaurantResponse createRestaurant(@Valid @RequestBody CreateRestaurantRequest request) {
        return AdminRestaurantResponse.from(
                catalogAdminService.createRestaurant(request.ownerUserId(), request.name(), request.address()));
    }

    @GetMapping("/restaurants")
    PageResponse<AdminRestaurantResponse> listRestaurants(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(catalogAdminService.listRestaurants(page, size), AdminRestaurantResponse::from);
    }

    @GetMapping("/restaurants/{id}")
    AdminRestaurantResponse getRestaurant(@PathVariable UUID id) {
        return AdminRestaurantResponse.from(catalogAdminService.getRestaurant(id));
    }

    @PutMapping("/restaurants/{id}")
    AdminRestaurantResponse updateRestaurant(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateRestaurantRequest request) {
        return AdminRestaurantResponse.from(catalogAdminService.updateRestaurant(
                id, request.name(), request.address(), CatalogStatus.valueOf(request.status()), request.version()));
    }

    // ---------- product ----------

    @PostMapping("/restaurants/{id}/products")
    @ResponseStatus(HttpStatus.CREATED)
    AdminProductResponse createProduct(@PathVariable UUID id, @Valid @RequestBody CreateProductRequest request) {
        return AdminProductResponse.from(catalogAdminService.createProduct(
                id, request.name(), request.description(), request.price(), request.initialStock()));
    }

    @GetMapping("/restaurants/{id}/products")
    PageResponse<AdminProductResponse> listProducts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResponse.from(catalogAdminService.listProducts(id, page, size), AdminProductResponse::from);
    }

    @GetMapping("/products/{id}")
    AdminProductResponse getProduct(@PathVariable UUID id) {
        return AdminProductResponse.from(catalogAdminService.getProduct(id));
    }

    @PutMapping("/products/{id}")
    AdminProductResponse updateProduct(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return AdminProductResponse.from(catalogAdminService.updateProduct(
                id, request.name(), request.description(), request.price(),
                CatalogStatus.valueOf(request.status()), request.version()));
    }

    @PostMapping("/products/{id}/stock-adjustments")
    AdminProductResponse adjustStock(@PathVariable UUID id, @Valid @RequestBody StockAdjustmentRequest request) {
        return AdminProductResponse.from(catalogAdminService.adjustStock(id, request.delta()));
    }
}
