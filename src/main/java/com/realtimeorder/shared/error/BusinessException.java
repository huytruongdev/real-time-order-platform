package com.realtimeorder.shared.error;

/**
 * Base class cho exception nghiệp vụ của mọi module.
 *
 * {@code code} là mã lỗi ổn định cho client (ví dụ {@code EMAIL_ALREADY_USED}),
 * không thay đổi khi message thay đổi.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorType type;
    private final String code;

    protected BusinessException(ErrorType type, String code, String message) {
        super(message);
        this.type = type;
        this.code = code;
    }

    public ErrorType getType() {
        return type;
    }

    public String getCode() {
        return code;
    }
}
