-- Phase 2: Catalog module - products và inventory (ADR-035)
-- Inventory nằm trực tiếp trong products. Order chỉ truy cập qua interface của Catalog,
-- nên sau này vẫn có thể tách thành bảng riêng mà không ảnh hưởng Order.

CREATE TABLE products (
    id              UUID          PRIMARY KEY,
    -- FK trong cùng module Catalog được phép.
    restaurant_id   UUID          NOT NULL REFERENCES restaurants (id),
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    -- Tiền: NUMERIC chính xác tuyệt đối, không dùng float/double. Một currency duy nhất (VND).
    price           NUMERIC(12, 2) NOT NULL,
    -- available_stock: còn có thể đặt. reserved_stock: đang được giữ cho order (Phase 3).
    available_stock INTEGER       NOT NULL,
    reserved_stock  INTEGER       NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    -- Optimistic locking cho thông tin product (name/description/price/status).
    -- Stock được thay đổi bằng atomic UPDATE riêng và KHÔNG tăng version.
    version         BIGINT        NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,

    CONSTRAINT ck_products_price_positive CHECK (price > 0),
    -- Chốt chặn cuối cùng chống oversell: database từ chối mọi UPDATE làm stock âm.
    CONSTRAINT ck_products_available_stock CHECK (available_stock >= 0),
    CONSTRAINT ck_products_reserved_stock CHECK (reserved_stock >= 0),
    CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_products_restaurant_id ON products (restaurant_id);
