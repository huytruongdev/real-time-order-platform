-- Phase 3: Order module (ADR-036)
-- customer_id, restaurant_id, product_id tham chiếu bảng của module khác nên không có FK (ADR-035).

CREATE TABLE orders (
    id            UUID           PRIMARY KEY,
    customer_id   UUID           NOT NULL,
    restaurant_id UUID           NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    -- Chỉ có giá trị khi status = CANCELLED.
    cancel_reason VARCHAR(30),
    -- Tổng tiền do server tính từ snapshot giá của các item.
    total_amount  NUMERIC(12, 2) NOT NULL,
    -- Optimistic locking: customer cancel và restaurant confirm cùng lúc chỉ một bên thắng.
    version       BIGINT         NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,

    CONSTRAINT ck_orders_status CHECK (status IN (
        'CREATED', 'CONFIRMED', 'PREPARING', 'READY', 'DRIVER_ASSIGNED',
        'PICKING_UP', 'DELIVERING', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_orders_cancel_reason CHECK (cancel_reason IN (
        'CUSTOMER_CANCELLED', 'RESTAURANT_REJECTED', 'PAYMENT_EXPIRED')),
    CONSTRAINT ck_orders_cancel_reason_only_when_cancelled CHECK (
        (status = 'CANCELLED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT ck_orders_total_amount CHECK (total_amount > 0)
);

-- Customer xem order của mình, mới nhất trước.
CREATE INDEX idx_orders_customer_id_created_at ON orders (customer_id, created_at DESC);
-- Restaurant xem order của restaurant mình theo status.
CREATE INDEX idx_orders_restaurant_id_status ON orders (restaurant_id, status);

CREATE TABLE order_items (
    id           UUID           PRIMARY KEY,
    order_id     UUID           NOT NULL REFERENCES orders (id),
    product_id   UUID           NOT NULL,
    -- Snapshot tại thời điểm đặt hàng: Admin đổi tên/giá sau đó không làm thay đổi order cũ.
    product_name VARCHAR(200)   NOT NULL,
    unit_price   NUMERIC(12, 2) NOT NULL,
    quantity     INTEGER        NOT NULL,
    subtotal     NUMERIC(12, 2) NOT NULL,

    CONSTRAINT uk_order_items_order_product UNIQUE (order_id, product_id),
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_unit_price CHECK (unit_price > 0),
    CONSTRAINT ck_order_items_subtotal CHECK (subtotal = unit_price * quantity)
);

CREATE TABLE order_status_history (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL REFERENCES orders (id),
    -- NULL ở dòng đầu tiên (tạo order).
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    actor      VARCHAR(20) NOT NULL,
    -- User thực hiện; NULL khi actor = SYSTEM (ví dụ payment expiration).
    changed_by UUID,
    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_order_status_history_actor CHECK (actor IN ('CUSTOMER', 'RESTAURANT', 'DRIVER', 'SYSTEM')),
    CONSTRAINT ck_order_status_history_changed_by CHECK ((actor = 'SYSTEM') = (changed_by IS NULL))
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id, created_at);
