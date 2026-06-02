--liquibase formatted sql

--changeset kb:033-restore-md-document-title
-- Markdown 文档标题用于前端列表和问答引用展示，应保留用户上传的原始文件名。
-- 早期上传逻辑误把 sanitizeFilename 结果写入 title，导致中文文件名在引用卡片中显示为下划线。
UPDATE md_documents
SET title = original_filename,
    updated_at = NOW()
WHERE original_filename IS NOT NULL
  AND original_filename <> ''
  AND title IS DISTINCT FROM original_filename;
