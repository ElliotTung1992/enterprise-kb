--liquibase formatted sql
--changeset kb:026-add-document-visual-assets

CREATE TABLE IF NOT EXISTS document_assets (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id          UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    asset_type           VARCHAR(30) NOT NULL,
    diagram_type         VARCHAR(30),
    asset_index          INTEGER NOT NULL,
    original_path        TEXT,
    object_key           TEXT,
    thumbnail_object_key TEXT,
    mime_type            VARCHAR(100),
    file_size            BIGINT,
    section              TEXT,
    anchor_chunk_index   INTEGER,
    alt_text             TEXT,
    title                TEXT,
    source_code          TEXT,
    ocr_text             TEXT,
    caption              TEXT,
    summary              TEXT,
    entities             TEXT,
    manual_caption       TEXT,
    manual_summary       TEXT,
    status               VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count          INTEGER NOT NULL DEFAULT 0,
    next_retry_at        TIMESTAMPTZ,
    last_error           TEXT,
    content_hash         VARCHAR(128),
    metadata             JSONB,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, asset_index)
);

CREATE INDEX IF NOT EXISTS idx_document_assets_document_id ON document_assets(document_id);
CREATE INDEX IF NOT EXISTS idx_document_assets_status ON document_assets(status);
CREATE INDEX IF NOT EXISTS idx_document_assets_object_key ON document_assets(object_key);

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(40) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN IF NOT EXISTS asset_id UUID REFERENCES document_assets(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS section TEXT,
    ADD COLUMN IF NOT EXISTS anchor_chunk_index INTEGER;

CREATE INDEX IF NOT EXISTS idx_chunks_content_type ON document_chunks(content_type);
CREATE INDEX IF NOT EXISTS idx_chunks_asset_id ON document_chunks(asset_id);
CREATE INDEX IF NOT EXISTS idx_chunks_section ON document_chunks(document_id, section);
