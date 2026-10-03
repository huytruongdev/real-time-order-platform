package com.realtimeorder.user.application;

import com.realtimeorder.user.domain.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin user public ra ngoài User module. Không chứa password hash.
 *
 * {@code role} là String thay vì enum {@code Role} của domain, để module khác dùng record này
 * mà không phải phụ thuộc vào package domain của User module.
 */
public record UserProfile(UUID id, String email, String name, String role, Instant createdAt) {

    static UserProfile from(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getName(), user.getRole().name(), user.getCreatedAt());
    }
}
