-- Phase 1: User module
-- ID là UUIDv7 được sinh ở application layer (ADR-027), nên không có DEFAULT.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    -- Email được normalize (trim + lowercase) ở application trước khi lưu.
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('CUSTOMER', 'RESTAURANT', 'DRIVER', 'ADMIN'))
);
