--liquibase formatted sql
--changeset kb:005-create-documents

CREATE TABLE IF NOT EXISTS documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id          UUID NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    uploaded_by       UUID NOT NULL REFERENCES users(id),
    title             VARCHAR(500) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    file_path         TEXT NOT NULL,
    file_size_bytes   BIGINT,
    mime_type         VARCHAR(100),
    source_url        TEXT,
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message     TEXT,
    chunk_count       INTEGER DEFAULT 0,
    language          VARCHAR(20),
    metadata          JSONB,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_documents_space_id ON documents(space_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_documents_status ON documents(status) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_documents_metadata ON documents USING GIN(metadata);
