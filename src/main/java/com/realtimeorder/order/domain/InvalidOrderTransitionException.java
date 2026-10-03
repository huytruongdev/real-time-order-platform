package com.realtimeorder.order.domain;

import com.realtimeorder.shared.error.BusinessException;
import com.realtimeorder.shared.error.ErrorType;

/** Thao tác không hợp lệ với trạng thái hiện tại của Order (ví dụ confirm order đã CANCELLED). */
public class InvalidOrderTransitionException extends BusinessException {

    public InvalidOrderTransitionException(OrderStatus from, OrderStatus to) {
        super(ErrorType.CONFLICT, "INVALID_ORDER_TRANSITION",
                "Không thể chuyển order từ " + from + " sang " + to);
    }
}
