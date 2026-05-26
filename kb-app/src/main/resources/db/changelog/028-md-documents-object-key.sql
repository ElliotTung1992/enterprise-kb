--liquibase formatted sql

--changeset kb:028-md-documents-object-key
ALTER TABLE md_documents ADD COLUMN IF NOT EXISTS object_key VARCHAR(1024);
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'md_documents'
          AND column_name = 'file_path'
    ) THEN
        UPDATE md_documents SET object_key = file_path WHERE object_key IS NULL AND file_path IS NOT NULL;
    END IF;
END $$;
ALTER TABLE md_documents DROP COLUMN IF EXISTS file_path;
