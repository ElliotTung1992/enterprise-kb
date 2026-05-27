--liquibase formatted sql

--changeset kb:030-add-md-document-assets
CREATE TABLE IF NOT EXISTS md_document_assets (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id    UUID NOT NULL REFERENCES md_documents(id) ON DELETE CASCADE,
    space_id       UUID NOT NULL,
    child_chunk_id UUID REFERENCES md_child_chunk(id) ON DELETE SET NULL,
    asset_index    INT NOT NULL,
    image_url      TEXT NOT NULL,
    object_key     TEXT NOT NULL,
    mime_type      VARCHAR(100),
    file_size      BIGINT,
    section        VARCHAR(1024),
    alt_text       TEXT,
    title          TEXT,
    ocr_text       TEXT,
    caption        TEXT,
    summary        TEXT,
    entities       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, asset_index)
);

CREATE INDEX IF NOT EXISTS idx_md_assets_document ON md_document_assets(document_id);
CREATE INDEX IF NOT EXISTS idx_md_assets_space ON md_document_assets(space_id);
CREATE INDEX IF NOT EXISTS idx_md_assets_child ON md_document_assets(child_chunk_id);
CREATE INDEX IF NOT EXISTS idx_md_assets_object_key ON md_document_assets(object_key);

ALTER TABLE md_child_chunk
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(40) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS asset_id UUID REFERENCES md_document_assets(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_md_child_content_type ON md_child_chunk(content_type);
CREATE INDEX IF NOT EXISTS idx_md_child_asset ON md_child_chunk(asset_id);

