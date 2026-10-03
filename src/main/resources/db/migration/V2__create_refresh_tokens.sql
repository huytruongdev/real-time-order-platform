-- Phase 1: Refresh token (ADR-026)
-- Chỉ lưu SHA-256 hash của token, không lưu raw token.
--
-- Rotation: mỗi lần refresh, token hiện tại bị revoke và trỏ tới token mới qua replaced_by.
-- Mọi token sinh ra từ cùng một lần login có chung family_id.
-- Nếu một token đã bị revoke được dùng lại (reuse) thì revoke toàn bộ family.

CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL,
    family_id   UUID        NOT NULL,
    -- SHA-256 hex = 64 ký tự
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID,
    created_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
