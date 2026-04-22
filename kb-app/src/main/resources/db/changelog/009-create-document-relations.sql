--liquibase formatted sql
--changeset kb:009-create-document-relations

CREATE TABLE IF NOT EXISTS document_relations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_doc_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    target_doc_id   UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    relation_type   VARCHAR(100) NOT NULL,
    weight          NUMERIC(5,4) DEFAULT 1.0,
    auto_detected   BOOLEAN DEFAULT FALSE,
    created_by      UUID REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (source_doc_id, target_doc_id, relation_type)
);

CREATE INDEX IF NOT EXISTS idx_doc_rel_source ON document_relations(source_doc_id);
CREATE INDEX IF NOT EXISTS idx_doc_rel_target ON document_relations(target_doc_id);
