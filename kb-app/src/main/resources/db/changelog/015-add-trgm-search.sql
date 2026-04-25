--liquibase formatted sql
--changeset kb:015-add-trgm-search

-- 启用 pg_trgm 扩展（支持三元组相似度检索，兼容中英文子串匹配）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 在 content 上建 GIN 索引，加速 ILIKE / similarity 查询
CREATE INDEX IF NOT EXISTS idx_chunks_content_trgm ON document_chunks USING GIN (content gin_trgm_ops);
