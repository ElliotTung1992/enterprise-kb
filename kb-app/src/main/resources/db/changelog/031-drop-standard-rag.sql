-- liquibase formatted sql
-- changeset system:031-drop-standard-rag

-- 标准（非 Markdown）RAG 竖井全退役：删除标准文档入库链与知识图谱的全部数据表。
-- Markdown 竖井（md_documents / md_parent_chunk / md_child_chunk / md_document_asset）与
-- 会话、客服、投诉、Trace、Eval 等表均不受影响。
-- 先删子表再删父表，CASCADE 兜底清理残留的外键 / 索引等依赖对象。

DROP TABLE IF EXISTS document_chunks CASCADE;
DROP TABLE IF EXISTS document_assets CASCADE;
DROP TABLE IF EXISTS document_relations CASCADE;
DROP TABLE IF EXISTS document_tags CASCADE;
DROP TABLE IF EXISTS tags CASCADE;
DROP TABLE IF EXISTS documents CASCADE;
