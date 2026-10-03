package com.realtimeorder.user.api;

import com.realtimeorder.user.application.AuthTokens;
import com.realtimeorder.user.application.UserProfile;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Request/response của User module API.
 */
final class AuthDtos {

    private AuthDtos() {
    }

    record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            // BCrypt chỉ dùng 72 byte đầu của password; giới hạn để không âm thầm cắt bớt.
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String name) {
    }

    record CreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String name,
            // ADMIN chỉ được tạo bằng bootstrap, CUSTOMER tự đăng ký.
            @NotBlank @Pattern(regexp = "RESTAURANT|DRIVER", message = "phải là RESTAURANT hoặc DRIVER") String role) {
    }

    record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    record RefreshTokenRequest(
            @NotBlank String refreshToken) {
    }

    record TokenResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt) {

        static TokenResponse from(AuthTokens tokens) {
            return new TokenResponse(
                    "Bearer",
                    tokens.accessToken(),
                    tokens.accessTokenExpiresAt(),
                    tokens.refreshToken(),
                    tokens.refreshTokenExpiresAt());
        }
    }

    record UserResponse(UUID id, String email, String name, String role, Instant createdAt) {

        static UserResponse from(UserProfile profile) {
            return new UserResponse(profile.id(), profile.email(), profile.name(), profile.role(), profile.createdAt());
        }
    }
}
