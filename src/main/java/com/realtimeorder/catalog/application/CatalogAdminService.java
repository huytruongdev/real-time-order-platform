package com.realtimeorder.catalog.application;

import com.realtimeorder.catalog.application.CatalogExceptions.InsufficientStockException;
import com.realtimeorder.catalog.application.CatalogExceptions.InvalidRestaurantOwnerException;
import com.realtimeorder.catalog.application.CatalogExceptions.InvalidStockAdjustmentException;
import com.realtimeorder.catalog.application.CatalogExceptions.ProductNotFoundException;
import com.realtimeorder.catalog.application.CatalogExceptions.RestaurantNotFoundException;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.Product;
import com.realtimeorder.catalog.domain.Restaurant;
import com.realtimeorder.catalog.infrastructure.ProductRepository;
import com.realtimeorder.catalog.infrastructure.RestaurantRepository;
import com.realtimeorder.shared.error.StaleVersionException;
import com.realtimeorder.user.application.UserQuery;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/**
 * Quản lý catalog cho Admin: thấy cả restaurant/product INACTIVE.
 *
 * Optimistic locking qua HTTP: client gửi {@code version} đã đọc được. Có hai lớp kiểm tra:
 * 1. So sánh version client gửi với version trong DB: phát hiện Admin khác đã sửa
 *    trong khoảng thời gian giữa lúc GET và lúc PUT (có thể là vài phút).
 * 2. {@code @Version} của Hibernate ({@code UPDATE ... WHERE version = ?}): phát hiện request khác
 *    commit trong khoảng vài mili giây giữa lúc đọc entity và lúc flush của chính transaction này.
 */
@Service
public class CatalogAdminService {

    private static final String RESTAURANT_ROLE = "RESTAURANT";

    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final UserQuery userQuery;
    private final Clock clock;

    public CatalogAdminService(RestaurantRepository restaurantRepository,
                               ProductRepository productRepository,
                               UserQuery userQuery,
                               Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.productRepository = productRepository;
        this.userQuery = userQuery;
        this.clock = clock;
    }

    // ---------- restaurant ----------

    /**
     * Không có FK restaurants.owner_user_id → users (ADR-035), nên owner được kiểm tra tại đây
     * qua public interface của User module.
     */
    @Transactional
    public RestaurantView createRestaurant(UUID ownerUserId, String name, String address) {
        boolean ownerIsRestaurantUser = userQuery.findById(ownerUserId)
                .map(owner -> RESTAURANT_ROLE.equals(owner.role()))
                .orElse(false);
        if (!ownerIsRestaurantUser) {
            throw new InvalidRestaurantOwnerException();
        }

        Restaurant restaurant = Restaurant.create(ownerUserId, name, address, clock.instant());
        // saveAndFlush để version (Hibernate gán khi persist) đã có giá trị trong response.
        return RestaurantView.from(restaurantRepository.saveAndFlush(restaurant));
    }

    @Transactional
    public RestaurantView updateRestaurant(UUID restaurantId, String name, String address,
                                           CatalogStatus status, long expectedVersion) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);
        if (restaurant.getVersion() != expectedVersion) {
            throw new StaleVersionException();
        }

        restaurant.update(name, address, status, clock.instant());
        // flush trong transaction để version mới (và lỗi optimistic lock, nếu có) xuất hiện ngay tại đây.
        return RestaurantView.from(restaurantRepository.saveAndFlush(restaurant));
    }

    @Transactional(readOnly = true)
    public Page<RestaurantView> listRestaurants(int page, int size) {
        return restaurantRepository.findAll(CatalogQueryService.pageOf(page, size)).map(RestaurantView::from);
    }

    @Transactional(readOnly = true)
    public RestaurantView getRestaurant(UUID restaurantId) {
        return restaurantRepository.findById(restaurantId)
                .map(RestaurantView::from)
                .orElseThrow(RestaurantNotFoundException::new);
    }

    // ---------- product ----------

    @Transactional
    public ProductView createProduct(UUID restaurantId, String name, String description,
                                     BigDecimal price, int initialStock) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException();
        }
        Product product = Product.create(restaurantId, name, description, price, initialStock, clock.instant());
        return ProductView.from(productRepository.saveAndFlush(product));
    }

    @Transactional
    public ProductView updateProduct(UUID productId, String name, String description, BigDecimal price,
                                     CatalogStatus status, long expectedVersion) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
        if (product.getVersion() != expectedVersion) {
            throw new StaleVersionException();
        }

        product.update(name, description, price, status, clock.instant());
        return ProductView.from(productRepository.saveAndFlush(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductView> listProducts(UUID restaurantId, int page, int size) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new RestaurantNotFoundException();
        }
        return productRepository.findByRestaurantId(restaurantId, CatalogQueryService.pageOf(page, size))
                .map(ProductView::from);
    }

    @Transactional(readOnly = true)
    public ProductView getProduct(UUID productId) {
        return productRepository.findById(productId)
                .map(ProductView::from)
                .orElseThrow(ProductNotFoundException::new);
    }

    /**
     * Nhập kho (delta dương) hoặc điều chỉnh giảm (delta âm, ví dụ hàng hỏng).
     *
     * Dùng delta thay vì set giá trị tuyệt đối: nếu Admin set "available = 50" đúng lúc Customer
     * đang reserve, giá trị tuyệt đối sẽ ghi đè phần vừa reserve. Delta được cộng atomic trong DB
     * nên các thao tác đồng thời không ghi đè nhau.
     */
    @Transactional
    public ProductView adjustStock(UUID productId, int delta) {
        if (delta == 0) {
            throw new InvalidStockAdjustmentException();
        }
        if (!productRepository.existsById(productId)) {
            throw new ProductNotFoundException();
        }
        if (productRepository.adjustAvailableStock(productId, delta, clock.instant()) == 0) {
            // Product chắc chắn tồn tại (không xoá cứng), nên 0 row nghĩa là không đủ stock.
            throw new InsufficientStockException();
        }
        // adjustAvailableStock đã clear persistence context, nên lần đọc này lấy giá trị mới từ DB.
        return getProduct(productId);
    }
}
