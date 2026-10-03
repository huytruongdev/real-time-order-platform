package com.realtimeorder.catalog.application;

import com.realtimeorder.catalog.application.CatalogExceptions.RestaurantNotFoundException;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.infrastructure.ProductRepository;
import com.realtimeorder.catalog.infrastructure.RestaurantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Đọc catalog cho Customer: chỉ thấy restaurant/product ACTIVE.
 */
@Service
public class CatalogQueryService {

    /**
     * Sort phải xác định (deterministic) khi phân trang bằng offset: nếu chỉ sort theo name,
     * các row trùng name có thể đổi thứ tự giữa hai lần query và bị lặp/mất giữa các trang.
     * Thêm id làm tiêu chí phụ vì id là duy nhất.
     */
    static final Sort SORT_BY_NAME = Sort.by("name", "id");

    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;

    public CatalogQueryService(RestaurantRepository restaurantRepository, ProductRepository productRepository) {
        this.restaurantRepository = restaurantRepository;
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public Page<RestaurantView> listActiveRestaurants(int page, int size) {
        return restaurantRepository.findByStatus(CatalogStatus.ACTIVE, pageOf(page, size))
                .map(RestaurantView::from);
    }

    @Transactional(readOnly = true)
    public RestaurantView getActiveRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .filter(restaurant -> restaurant.isActive())
                .map(RestaurantView::from)
                .orElseThrow(RestaurantNotFoundException::new);
    }

    /** Restaurant INACTIVE được xem như không tồn tại với Customer. */
    @Transactional(readOnly = true)
    public Page<ProductView> listActiveProducts(UUID restaurantId, int page, int size) {
        if (!restaurantRepository.existsByIdAndStatus(restaurantId, CatalogStatus.ACTIVE)) {
            throw new RestaurantNotFoundException();
        }
        return productRepository.findByRestaurantIdAndStatus(restaurantId, CatalogStatus.ACTIVE, pageOf(page, size))
                .map(ProductView::from);
    }

    static Pageable pageOf(int page, int size) {
        return PageRequest.of(page, size, SORT_BY_NAME);
    }
}
