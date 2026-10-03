package com.realtimeorder.catalog.application;

import com.realtimeorder.catalog.application.CatalogExceptions.InsufficientStockException;
import com.realtimeorder.catalog.application.CatalogExceptions.ProductNotAvailableException;
import com.realtimeorder.catalog.application.CatalogExceptions.RestaurantNotFoundException;
import com.realtimeorder.catalog.domain.CatalogStatus;
import com.realtimeorder.catalog.domain.InventoryReservation;
import com.realtimeorder.catalog.domain.Product;
import com.realtimeorder.catalog.domain.ReservationStatus;
import com.realtimeorder.catalog.infrastructure.InventoryReservationRepository;
import com.realtimeorder.catalog.infrastructure.ProductRepository;
import com.realtimeorder.catalog.infrastructure.RestaurantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public interface inventory của Catalog cho module khác (ADR-029, ADR-036).
 *
 * Mọi method dùng {@code @Transactional} mặc định (REQUIRED): khi Order gọi trong transaction của
 * mình, thay đổi stock và thay đổi order nằm trong CÙNG một database transaction, cùng commit
 * hoặc cùng rollback. Đây là lợi thế của Modular Monolith so với Microservices.
 */
@Service
public class InventoryService {

    /** Thứ tự lock cố định giữa mọi transaction: theo productId tăng dần. */
    private static final Comparator<UUID> LOCK_ORDER = Comparator.naturalOrder();

    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final InventoryReservationRepository reservationRepository;
    private final Clock clock;

    public InventoryService(RestaurantRepository restaurantRepository,
                            ProductRepository productRepository,
                            InventoryReservationRepository reservationRepository,
                            Clock clock) {
        this.restaurantRepository = restaurantRepository;
        this.productRepository = productRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
    }

    /**
     * Reserve stock cho một order. Tất cả hoặc không gì cả: dòng nào không đủ stock thì exception
     * làm rollback cả transaction, kể cả các dòng đã reserve trước đó.
     *
     * Tránh deadlock: order A mua (P1, P2), order B mua (P2, P1). Nếu A lock P1 rồi chờ P2, còn B lock
     * P2 rồi chờ P1, hai transaction chờ nhau mãi (PostgreSQL sẽ phát hiện và huỷ một bên).
     * Luôn UPDATE theo productId tăng dần thì không thể có vòng chờ.
     *
     * @param orderId      order giữ reservation; chưa cần tồn tại trong bảng orders
     * @param restaurantId mọi product phải thuộc restaurant này
     * @param lines        không rỗng, không trùng productId
     * @return snapshot tên/giá theo đúng thứ tự {@code lines}
     */
    @Transactional
    public List<ReservedItem> reserve(UUID orderId, UUID restaurantId, List<ReservationLine> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("lines không được rỗng");
        }
        Set<UUID> productIds = new HashSet<>();
        for (ReservationLine line : lines) {
            if (!productIds.add(line.productId())) {
                throw new IllegalArgumentException("productId bị trùng: " + line.productId());
            }
        }
        if (!restaurantRepository.existsByIdAndStatus(restaurantId, CatalogStatus.ACTIVE)) {
            throw new RestaurantNotFoundException();
        }

        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        for (UUID productId : productIds) {
            Product product = products.get(productId);
            if (product == null || !product.isActive() || !product.getRestaurantId().equals(restaurantId)) {
                throw new ProductNotAvailableException(productId);
            }
        }

        Instant now = clock.instant();
        lines.stream()
                .sorted(Comparator.comparing(ReservationLine::productId, LOCK_ORDER))
                .forEach(line -> {
                    if (productRepository.reserveStock(line.productId(), line.quantity(), now) == 0) {
                        throw new InsufficientStockException(line.productId());
                    }
                    reservationRepository.save(
                            InventoryReservation.reserve(orderId, line.productId(), line.quantity(), now));
                });

        return lines.stream()
                .map(line -> {
                    Product product = products.get(line.productId());
                    return new ReservedItem(product.getId(), product.getName(), product.getPrice(), line.quantity());
                })
                .toList();
    }

    /**
     * Trả lại toàn bộ reservation còn RESERVED của order về available.
     * Idempotent: gọi lại (hoặc order không có reservation) thì không làm gì.
     */
    @Transactional
    public void release(UUID orderId) {
        Instant now = clock.instant();
        for (InventoryReservation reservation : reservationRepository.findForUpdate(orderId, ReservationStatus.RESERVED)) {
            requireOneRow(productRepository.releaseStock(reservation.getProductId(), reservation.getQuantity(), now),
                    reservation);
            reservation.release(now);
        }
    }

    /**
     * Chuyển reservation còn RESERVED của order thành đã bán (Payment SUCCESS, phase Payment).
     * Idempotent như {@link #release}.
     */
    @Transactional
    public void commit(UUID orderId) {
        Instant now = clock.instant();
        for (InventoryReservation reservation : reservationRepository.findForUpdate(orderId, ReservationStatus.RESERVED)) {
            requireOneRow(productRepository.commitStock(reservation.getProductId(), reservation.getQuantity(), now),
                    reservation);
            reservation.commit(now);
        }
    }

    /**
     * reserved_stock nhỏ hơn quantity của một reservation đang RESERVED nghĩa là dữ liệu đã lệch
     * (bug, hoặc ai đó sửa DB bằng tay). Dừng lại thay vì làm stock sai thêm.
     */
    private static void requireOneRow(int updatedRows, InventoryReservation reservation) {
        if (updatedRows != 1) {
            throw new IllegalStateException("reserved_stock của product " + reservation.getProductId()
                    + " không khớp với reservation " + reservation.getId());
        }
    }
}
