package com.realtimeorder.shared.error;

/**
 * Client gửi {@code version} cũ hơn version hiện tại trong database: dữ liệu client đang sửa
 * đã bị người khác thay đổi. Client phải đọc lại rồi sửa tiếp (optimistic locking).
 */
public class StaleVersionException extends BusinessException {

    public static final String CODE = "CONCURRENT_MODIFICATION";

    public StaleVersionException() {
        super(ErrorType.CONFLICT, CODE, "Dữ liệu đã bị thay đổi bởi request khác, hãy tải lại và thử lại");
    }
}
