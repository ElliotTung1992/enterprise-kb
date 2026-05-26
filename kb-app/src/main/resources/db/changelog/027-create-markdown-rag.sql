--liquibase formatted sql

--changeset kb:027-create-markdown-rag
CREATE TABLE IF NOT EXISTS md_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id UUID NOT NULL,
    uploaded_by UUID,
    title VARCHAR(512),
    original_filename VARCHAR(512),
    object_key VARCHAR(1024),
    file_size_bytes BIGINT,
    mime_type VARCHAR(128),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    chunk_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    metadata JSONB,
    heading_tree JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_md_documents_space ON md_documents(space_id);
CREATE INDEX IF NOT EXISTS idx_md_documents_status ON md_documents(status);

CREATE TABLE IF NOT EXISTS md_parent_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES md_documents(id) ON DELETE CASCADE,
    space_id UUID NOT NULL,
    section VARCHAR(1024),
    heading_level INT,
    content TEXT NOT NULL,
    ordinal INT NOT NULL,
    child_count INT NOT NULL DEFAULT 0,
    char_start INT,
    char_end INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_md_parent_document ON md_parent_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_md_parent_space ON md_parent_chunk(space_id);

CREATE TABLE IF NOT EXISTS md_child_chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID NOT NULL REFERENCES md_parent_chunk(id) ON DELETE CASCADE,
    document_id UUID NOT NULL,
    space_id UUID NOT NULL,
    section VARCHAR(1024),
    seq_in_parent INT NOT NULL,
    embed_text TEXT NOT NULL,
    token_count INT,
    embedding_model VARCHAR(64),
    char_start INT NOT NULL,
    char_end INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_md_child_parent ON md_child_chunk(parent_id);
CREATE INDEX IF NOT EXISTS idx_md_child_document ON md_child_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_md_child_space ON md_child_chunk(space_id);
CREATE INDEX IF NOT EXISTS idx_md_child_trgm ON md_child_chunk USING gin (embed_text gin_trgm_ops);
