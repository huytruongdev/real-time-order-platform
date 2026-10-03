-- Phase 3: Catalog module - reservation theo từng order (ADR-036)
--
-- Ghi lại order nào đang giữ bao nhiêu của product nào, để:
-- - release/commit biết chính xác số lượng mà không phải tin dữ liệu do Order truyền vào;
-- - release/commit idempotent: gọi lần hai không trả stock thêm lần nữa
--   (quan trọng khi Kafka giao event at-least-once ở Phase 6).
--
-- Trạng thái:
--   RESERVED  : đang giữ (available đã giảm, reserved đã tăng)
--   RELEASED  : đã trả lại (order cancel)
--   COMMITTED : đã bán (payment SUCCESS, reserved giảm)

CREATE TABLE inventory_reservations (
    id         UUID        PRIMARY KEY,
    -- Không có FK sang orders: không dùng FK giữa các module (ADR-035).
    order_id   UUID        NOT NULL,
    product_id UUID        NOT NULL REFERENCES products (id),
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    -- Một order giữ mỗi product tối đa một dòng. Index của constraint cũng phục vụ tìm theo order_id.
    CONSTRAINT uk_inventory_reservations_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_inventory_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservations_status CHECK (status IN ('RESERVED', 'RELEASED', 'COMMITTED'))
);
