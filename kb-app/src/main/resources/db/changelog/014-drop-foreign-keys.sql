-- liquibase formatted sql
-- changeset system:014-drop-foreign-keys

-- spaces
ALTER TABLE spaces DROP CONSTRAINT IF EXISTS spaces_owner_id_fkey;

-- user_space_roles
ALTER TABLE user_space_roles DROP CONSTRAINT IF EXISTS user_space_roles_user_id_fkey;
ALTER TABLE user_space_roles DROP CONSTRAINT IF EXISTS user_space_roles_space_id_fkey;
ALTER TABLE user_space_roles DROP CONSTRAINT IF EXISTS user_space_roles_granted_by_fkey;

-- documents
ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_space_id_fkey;
ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_uploaded_by_fkey;

-- document_chunks
ALTER TABLE document_chunks DROP CONSTRAINT IF EXISTS document_chunks_document_id_fkey;

-- tags
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_space_id_fkey;
ALTER TABLE tags DROP CONSTRAINT IF EXISTS tags_parent_id_fkey;

-- document_tags
ALTER TABLE document_tags DROP CONSTRAINT IF EXISTS document_tags_document_id_fkey;
ALTER TABLE document_tags DROP CONSTRAINT IF EXISTS document_tags_tag_id_fkey;
ALTER TABLE document_tags DROP CONSTRAINT IF EXISTS document_tags_tagged_by_fkey;

-- document_relations
ALTER TABLE document_relations DROP CONSTRAINT IF EXISTS document_relations_source_doc_id_fkey;
ALTER TABLE document_relations DROP CONSTRAINT IF EXISTS document_relations_target_doc_id_fkey;
ALTER TABLE document_relations DROP CONSTRAINT IF EXISTS document_relations_created_by_fkey;

-- refresh_tokens
ALTER TABLE refresh_tokens DROP CONSTRAINT IF EXISTS refresh_tokens_user_id_fkey;

-- audit_logs
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_user_id_fkey;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_space_id_fkey;

-- mcp_api_keys
ALTER TABLE mcp_api_keys DROP CONSTRAINT IF EXISTS mcp_api_keys_user_id_fkey;
