package com.realtimeorder.user.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // JPA
    }

    private User(String email, String passwordHash, String name, Role role, Instant now) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.name = name.trim();
        this.role = role;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Self-registration luôn tạo CUSTOMER. Các role khác không được tự đăng ký. */
    public static User registerCustomer(String email, String passwordHash, String name, Instant now) {
        return new User(email, passwordHash, name, Role.CUSTOMER, now);
    }

    /** User có role bất kỳ, chỉ dùng cho luồng do Admin hoặc hệ thống tạo (ADR-035). */
    public static User createWithRole(String email, String passwordHash, String name, Role role, Instant now) {
        return new User(email, passwordHash, name, role, now);
    }

    /**
     * Email là định danh đăng nhập: "A@x.com" và "a@x.com " phải là cùng một user.
     * Normalize trước khi lưu để unique constraint trong DB có hiệu lực đúng.
     */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
