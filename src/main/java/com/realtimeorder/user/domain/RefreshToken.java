package com.realtimeorder.user.domain;

import com.realtimeorder.shared.id.IdGenerator;
import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token được lưu server-side (ADR-026). Chỉ lưu hash, không lưu raw token.
 *
 * Một "family" là chuỗi token sinh ra từ cùng một lần login qua các lần rotation.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
        // JPA
    }

    private RefreshToken(UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant now) {
        this.userId = userId;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    /** Token đầu tiên của một family mới (khi login). */
    public static RefreshToken issueNewFamily(UUID userId, String tokenHash, Instant expiresAt, Instant now) {
        return new RefreshToken(userId, IdGenerator.newId(), tokenHash, expiresAt, now);
    }

    /**
     * Rotation: revoke token hiện tại và tạo token kế tiếp trong cùng family.
     * Caller phải kiểm tra {@link #isActive(Instant)} trước khi gọi.
     */
    public RefreshToken rotate(String newTokenHash, Instant newExpiresAt, Instant now) {
        RefreshToken next = new RefreshToken(userId, familyId, newTokenHash, newExpiresAt, now);
        this.revokedAt = now;
        this.replacedBy = next.getId();
        return next;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }
}
