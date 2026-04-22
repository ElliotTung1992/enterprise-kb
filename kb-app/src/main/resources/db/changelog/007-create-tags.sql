--liquibase formatted sql
--changeset kb:007-create-tags

CREATE TABLE IF NOT EXISTS tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id    UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    slug        VARCHAR(200) NOT NULL,
    color       VARCHAR(7),
    parent_id   UUID REFERENCES tags(id),
    tag_type    VARCHAR(50) NOT NULL DEFAULT 'TAG',
    description TEXT,
    sort_order  INTEGER DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    UNIQUE (space_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_tags_space_id ON tags(space_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tags_parent_id ON tags(parent_id) WHERE deleted_at IS NULL;
