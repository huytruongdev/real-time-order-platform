package com.realtimeorder.order.application;

import com.realtimeorder.shared.error.BusinessException;
import com.realtimeorder.shared.error.ErrorType;

/**
 * Exception nghiệp vụ của Order module.
 */
public final class OrderExceptions {

    private OrderExceptions() {
    }

    /**
     * Dùng cả khi order tồn tại nhưng không thuộc về user đang gọi: trả 404 thay vì 403
     * để không tiết lộ một orderId có tồn tại hay không.
     */
    public static final class OrderNotFoundException extends BusinessException {
        public OrderNotFoundException() {
            super(ErrorType.NOT_FOUND, "ORDER_NOT_FOUND", "Order không tồn tại");
        }
    }

    public static final class DuplicateOrderItemException extends BusinessException {
        public DuplicateOrderItemException() {
            super(ErrorType.BAD_REQUEST, "DUPLICATE_ORDER_ITEM", "Mỗi product chỉ được xuất hiện một lần trong order");
        }
    }
}
