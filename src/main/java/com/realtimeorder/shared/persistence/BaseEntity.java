package com.realtimeorder.shared.persistence;

import com.realtimeorder.shared.id.IdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Base class cho entity có UUID được sinh ở application layer.
 *
 * Vì ID đã có giá trị trước khi persist, Spring Data không thể dựa vào "id == null" để biết
 * entity là mới. Nếu không implement {@link Persistable}, {@code save()} sẽ gọi {@code merge()}
 * và phát sinh thêm một câu SELECT trước mỗi INSERT.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected BaseEntity() {
        this.id = IdGenerator.newId();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
