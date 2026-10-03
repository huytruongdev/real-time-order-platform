package com.realtimeorder.user.application;

import com.realtimeorder.shared.error.BusinessException;
import com.realtimeorder.shared.error.ErrorType;

/**
 * Exception nghiệp vụ của User module.
 */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static final class EmailAlreadyUsedException extends BusinessException {
        public EmailAlreadyUsedException() {
            super(ErrorType.CONFLICT, "EMAIL_ALREADY_USED", "Email đã được sử dụng");
        }
    }

    /**
     * Dùng chung cho "email không tồn tại" và "sai password" để không tiết lộ email nào đã đăng ký
     * (chống user enumeration).
     */
    public static final class InvalidCredentialsException extends BusinessException {
        public InvalidCredentialsException() {
            super(ErrorType.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email hoặc password không đúng");
        }
    }

    /**
     * Dùng chung cho token không tồn tại, hết hạn, đã revoke hoặc bị reuse.
     * Client không cần biết lý do cụ thể: trường hợp nào cũng phải login lại.
     */
    public static final class InvalidRefreshTokenException extends BusinessException {
        public InvalidRefreshTokenException() {
            super(ErrorType.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token không hợp lệ");
        }
    }

    public static final class UserNotFoundException extends BusinessException {
        public UserNotFoundException() {
            super(ErrorType.NOT_FOUND, "USER_NOT_FOUND", "User không tồn tại");
        }
    }
}
