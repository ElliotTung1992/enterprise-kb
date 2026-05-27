# 数据库迁移

## 工具

**Liquibase**，随应用启动自动执行。

- Master 文件：`kb-app/src/main/resources/db/changelog/db.changelog-master.xml`
- 迁移 SQL：`kb-app/src/main/resources/db/changelog/001-xxx.sql` ... `017-xxx.sql`

## 添加新迁移

1. 在 `db/changelog/` 新建 `018-xxx.sql`（编号连续）
2. 在 `db.changelog-master.xml` 末尾添加 `<include file="db/changelog/018-xxx.sql" .../>`
3. 应用启动时自动执行

## 迁移历史摘要

| 编号 | 操作 | 关键内容 |
|------|------|---------|
| 001-004 | 建表 | roles, users, spaces, user_space_roles |
| 005-006 | 建表 | documents, document_chunks |
| 007-009 | 建表 | tags, document_tags, document_relations |
| 010-011 | 建表 | refresh_tokens, audit_logs |
| 012 | 初始化 | 默认角色数据 |
| 014 | Drop FK | 去掉部分外键约束，提升摄入写入性能 |
| 015 | 扩展 | 安装 `pg_trgm`，建全文检索 GIN 索引 |
| 016 | 建表 | qa_sessions, qa_messages |
| 017 | Drop | 删除 mcp_api_keys 表 |
| 027 | 建表 | md_documents, md_parent_chunk, md_child_chunk（结构感知 RAG，含 child `pg_trgm` GIN 索引）→ [[features/markdown-structure-rag]] |
| 028 | 扩展 | md_documents 增 object_key、迁移并删除旧 file_path |
| 029 | 扩展 | 装 `pg_tokenizer` + `vchord_bm25`，md_child_chunk 加 `bm25vector` 列 + `USING bm25` 索引（带前置守卫，仅 vchord-suite 镜像可装时执行）→ [[features/md-keyword-bm25]] |

> **非 migration 的配套运维脚本**：`db/manual/md-bm25-build.sql`（建 jieba analyzer/model/tokenizer/trigger + 回填）依赖语料、**不在 master changelog**、不自动执行，须语料 ingest 后手动跑。详见 [[features/md-keyword-bm25]]。

## 注意事项

- 迁移文件**不可修改**（Liquibase 用 checksum 校验）
- 需要改已有表，必须新建迁移文件
- 生产环境数据库密码通过 `PG_PASSWORD` 环境变量注入
