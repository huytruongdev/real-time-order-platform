package com.realtimeorder.catalog.application;

import com.realtimeorder.shared.error.BusinessException;
import com.realtimeorder.shared.error.ErrorType;

/**
 * Exception nghiệp vụ của Catalog module.
 */
public final class CatalogExceptions {

    private CatalogExceptions() {
    }

    /** Dùng cả khi restaurant tồn tại nhưng INACTIVE và được truy cập qua API public. */
    public static final class RestaurantNotFoundException extends BusinessException {
        public RestaurantNotFoundException() {
            super(ErrorType.NOT_FOUND, "RESTAURANT_NOT_FOUND", "Restaurant không tồn tại");
        }
    }

    public static final class ProductNotFoundException extends BusinessException {
        public ProductNotFoundException() {
            super(ErrorType.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product không tồn tại");
        }
    }

    public static final class InvalidRestaurantOwnerException extends BusinessException {
        public InvalidRestaurantOwnerException() {
            super(ErrorType.BAD_REQUEST, "INVALID_RESTAURANT_OWNER",
                    "Owner phải là user tồn tại và có role RESTAURANT");
        }
    }

    public static final class InvalidStockAdjustmentException extends BusinessException {
        public InvalidStockAdjustmentException() {
            super(ErrorType.BAD_REQUEST, "INVALID_STOCK_ADJUSTMENT", "delta phải khác 0");
        }
    }

    public static final class InsufficientStockException extends BusinessException {
        public InsufficientStockException() {
            super(ErrorType.CONFLICT, "INSUFFICIENT_STOCK", "Không đủ stock");
        }
    }
}
