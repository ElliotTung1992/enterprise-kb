--liquibase formatted sql

--changeset kb:029-add-md-bm25
-- 前置守卫：仅当 pg_tokenizer 与 vchord_bm25 两个扩展都「可安装」（即跑在 vchord-suite 镜像上）时才执行本迁移。
-- 否则 onFail:CONTINUE —— 跳过本次（且不标记已执行），应用照常启动、md 关键词走默认 TRGM；
-- 日后换上 vchord-suite 镜像重启时，本迁移会自动重新评估并执行。这样新扩展不再是应用启动的硬依赖。
--preconditions onFail:CONTINUE onError:CONTINUE
--precondition-sql-check expectedResult:2 SELECT count(*) FROM pg_available_extensions WHERE name IN ('pg_tokenizer', 'vchord_bm25')
-- md 关键词检索升级 BM25（VectorChord-bm25 + pg_tokenizer）。仅 md 竖井，标准竖井 document_chunks 不动。
-- 本迁移只做「对空表也安全、纯 SQL 可复现」的 schema 级改动：装扩展 + 加列 + 建索引。
-- jieba 的 text_analyzer / 自训练 custom model / tokenizer / 自动填充 trigger 依赖语料，
-- 无法对空表运行，故挪到运维脚本 db/manual/md-bm25-build.sql（语料 ingest 后手动执行）。
-- keyword-mode 默认仍 TRGM，本迁移 apply 后 BM25 链路尚未启用，不影响线上检索。

-- 1. 扩展（tensorchord/vchord-suite:pg16 镜像已含二进制）
CREATE EXTENSION IF NOT EXISTS pg_tokenizer CASCADE;
CREATE EXTENSION IF NOT EXISTS vchord_bm25 CASCADE;

-- 2. 加 bm25vector 列（可空：冷启动期 / flag=TRGM 期为 NULL，由 trigger 在 model 建好后自动回填）
ALTER TABLE md_child_chunk ADD COLUMN IF NOT EXISTS bm25vector bm25vector;

-- 3. BM25 索引（建在空 / 全 NULL 列上安全，随 trigger 写入自动维护）
CREATE INDEX IF NOT EXISTS idx_md_child_bm25 ON md_child_chunk USING bm25 (bm25vector bm25_ops);

--rollback DROP INDEX IF EXISTS idx_md_child_bm25;
--rollback ALTER TABLE md_child_chunk DROP COLUMN IF EXISTS bm25vector;
