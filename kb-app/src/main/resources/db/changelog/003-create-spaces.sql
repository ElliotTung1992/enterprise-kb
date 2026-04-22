--liquibase formatted sql
--changeset kb:003-create-spaces

CREATE TABLE IF NOT EXISTS spaces (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                     VARCHAR(255) NOT NULL,
    slug                     VARCHAR(100) NOT NULL UNIQUE,
    description              TEXT,
    owner_id                 UUID NOT NULL REFERENCES users(id),
    preferred_model_provider VARCHAR(30),
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at               TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_spaces_slug ON spaces(slug) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_spaces_owner ON spaces(owner_id) WHERE deleted_at IS NULL;
