package com.realtimeorder.catalog.domain;

import com.realtimeorder.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "restaurants")
public class Restaurant extends BaseEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CatalogStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Restaurant() {
        // JPA
    }

    private Restaurant(UUID ownerUserId, String name, String address, Instant now) {
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId");
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
        this.status = CatalogStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Restaurant mới luôn ACTIVE. */
    public static Restaurant create(UUID ownerUserId, String name, String address, Instant now) {
        return new Restaurant(ownerUserId, name, address, now);
    }

    public void update(String name, String address, CatalogStatus status, Instant now) {
        this.name = requireText(name, "name");
        this.address = requireText(address, "address");
        this.status = Objects.requireNonNull(status, "status");
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == CatalogStatus.ACTIVE;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " không được để trống");
        }
        return value.trim();
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public CatalogStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
