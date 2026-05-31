# 数据库迁移

## 工具

**Liquibase**，随应用启动自动执行。

- Master 文件：`kb-app/src/main/resources/db/changelog/db.changelog-master.xml`
- 迁移 SQL：`kb-app/src/main/resources/db/changelog/001-xxx.sql` ... `032-xxx.sql`

## 添加新迁移

1. 在 `db/changelog/` 新建 `033-xxx.sql`（编号连续）
2. 在 `db.changelog-master.xml` 末尾添加 `<include file="db/changelog/033-xxx.sql" relativeToChangelogFile="false"/>`
3. 应用启动时自动执行

## 迁移历史摘要

| 编号 | 操作 | 关键内容 |
|------|------|---------|
| 001-004 | 建表 | `roles`, `users`, `spaces`, `user_space_roles` |
| 005-006 | 建表 | `documents`, `document_chunks` *(迁移 031 已 drop)* |
| 007-009 | 建表 | `tags`, `document_tags`, `document_relations` *(迁移 031 已 drop)* |
| 010-011 | 建表 | `refresh_tokens`, `audit_logs` |
| 012 | 初始化 | 默认角色数据 |
| 013 | 建表 | `mcp_api_keys` *(017 已 drop)* |
| 014 | Drop FK | 去掉部分外键约束，提升摄入写入性能 |
| 015 | 扩展 | 安装 `pg_trgm`，建全文检索 GIN 索引 |
| 016 | 建表 | `qa_sessions`, `qa_messages`（md QA 共用） |
| 017 | Drop | 删除 `mcp_api_keys` 表 |
| 018 | 建表 | `review_requests`（HITL 售后审核申请） |
| 019 | 建表 | `customer_sessions`, `customer_messages`（商城客服） |
| 020 | Alter | `review_requests.space_id` 改为可空（客户助手无知识空间） |
| 021 | 索引 | `customer_sessions` 复合索引 |
| 022 | 建表 | 投诉升级 `complaint_*` 系列表 → [[features/complaint-escalation]] |
| 023 | Alter | `complaint_plan` 增 `next_check_at` |
| 024 | Alter | `customer_messages` 增 `domain` 列 → [[features/intent-routing]] |
| 025 | 建表 | `eval_*` 系列（离线评估）→ [[features/ragas-evaluation]] |
| 026 | 建表 | `document_visual_assets` *(迁移 031 已 drop)* |
| 027 | 建表 | `md_documents`, `md_parent_chunk`, `md_child_chunk`（结构感知 RAG，含 child `pg_trgm` GIN 索引）→ [[features/markdown-structure-rag]] |
| 028 | Alter | `md_documents` 增 `object_key`，迁移并删除旧 `file_path` |
| 029 | 扩展 | 装 `pg_tokenizer` + `vchord_bm25`，`md_child_chunk` 加 `bm25vector` 列 + `USING bm25` 索引（带前置守卫，仅 vchord-suite 镜像可装时执行）→ [[features/md-keyword-bm25]] |
| 030 | 建表 | `md_document_assets`（md 竖井图片资产） |
| **031** | **Drop** | **退役标准 RAG 竖井：`documents` / `document_chunks` / `document_assets` / `document_relations` / `document_tags` / `tags` 6 张表** |
| **032** | **Drop** | **退役自研 trace：`agent_traces` / `agent_trace_steps`（接续：[[features/langfuse-tracing]]）** |

> **非 migration 的配套运维脚本**：`db/manual/md-bm25-build.sql`（建 jieba analyzer/model/tokenizer/trigger + 回填）依赖语料、**不在 master changelog**、不自动执行，须语料 ingest 后手动跑。详见 [[features/md-keyword-bm25]]。

## 注意事项

- 迁移文件**不可修改**（Liquibase 用 checksum 校验）
- 需要改已有表，必须新建迁移文件
- 生产环境数据库密码通过 `PG_PASSWORD` 环境变量注入
