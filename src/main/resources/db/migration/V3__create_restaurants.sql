-- Phase 2: Catalog module - restaurants (ADR-035)

CREATE TABLE restaurants (
    id            UUID         PRIMARY KEY,
    -- User role RESTAURANT sở hữu restaurant này.
    -- Không có FK sang users: không dùng FK giữa bảng của các module khác nhau (ADR-035).
    -- Tính hợp lệ được kiểm tra ở application layer qua UserQuery của User module.
    owner_user_id UUID         NOT NULL,
    name          VARCHAR(200) NOT NULL,
    address       VARCHAR(500) NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    -- Optimistic locking cho thao tác sửa của Admin.
    version       BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_restaurants_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Phase 3: tìm các restaurant của một Restaurant user để kiểm tra quyền.
CREATE INDEX idx_restaurants_owner_user_id ON restaurants (owner_user_id);
