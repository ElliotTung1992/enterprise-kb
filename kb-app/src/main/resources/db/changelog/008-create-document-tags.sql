--liquibase formatted sql
--changeset kb:008-create-document-tags

CREATE TABLE IF NOT EXISTS document_tags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tag_id      UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    tagged_by   UUID REFERENCES users(id),
    auto_tagged BOOLEAN DEFAULT FALSE,
    confidence  NUMERIC(5,4),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (document_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_doc_tags_document_id ON document_tags(document_id);
CREATE INDEX IF NOT EXISTS idx_doc_tags_tag_id ON document_tags(tag_id);
