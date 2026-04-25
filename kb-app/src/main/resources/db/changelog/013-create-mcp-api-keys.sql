--liquibase formatted sql
--changeset kb:013-create-mcp-api-keys

CREATE TABLE IF NOT EXISTS mcp_api_keys (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    key_hash    VARCHAR(255) NOT NULL UNIQUE,
    key_prefix  VARCHAR(20)  NOT NULL,
    name        VARCHAR(100),
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_mcp_api_keys_user_id  ON mcp_api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_mcp_api_keys_key_hash ON mcp_api_keys(key_hash);
