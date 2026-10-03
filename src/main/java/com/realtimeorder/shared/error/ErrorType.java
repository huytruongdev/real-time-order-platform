package com.realtimeorder.shared.error;

/**
 * Loại lỗi nghiệp vụ, độc lập với HTTP.
 * Tầng web ({@link GlobalExceptionHandler}) quyết định mapping sang HTTP status.
 */
public enum ErrorType {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT
}
