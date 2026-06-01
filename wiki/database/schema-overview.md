# 数据库 Schema 总览

数据库：PostgreSQL 16，数据库名：`enterprise_kb`
Schema 通过 **Liquibase** 管理，changelog: `kb-app/src/main/resources/db/changelog/`

## 当前活跃表清单

| 表名 | 建表迁移 | 说明 |
|------|---------|------|
| `roles` | 001 | 系统角色定义 |
| `users` | 002 | 用户账户（BCrypt 密码） |
| `spaces` | 003 | 知识空间 |
| `user_space_roles` | 004 | 用户-空间-角色三元组 |
| `refresh_tokens` | 010 | JWT RefreshToken |
| `audit_logs` | 011 | 操作审计日志 |
| `qa_sessions` + `qa_messages` | 016 | QA 会话与消息（md QA 共用） |
| `review_requests` | 018 / 020 | HITL 售后审核申请（含对话快照、订单详情 JSONB；`space_id` 可空） |
| `customer_sessions` + `customer_messages` | 019 / 021 / 024 | 商城客服对话会话与消息（含 domain 列、复合索引） |
| `complaint_*`（多表） | 022 / 023 | 投诉升级 StateGraph 状态 / 计划 |
| `eval_*`（多表） | 025 | 离线评估用例 / 运行 |
| `md_documents` + `md_parent_chunk` + `md_child_chunk` | 027 / 028 / 029 | Markdown 结构感知 RAG（含 child `pg_trgm` GIN 索引 + `bm25vector` 列） |
| `md_document_assets` | 030 | md 竖井图片资产 |

## 已退役表（迁移 031 / 032）

| 表名 | 退役迁移 | 接续 |
|------|---------|------|
| `documents` | 031 | `md_documents` |
| `document_chunks` | 031 | `md_child_chunk` / `md_parent_chunk` |
| `document_assets` | 031 | `md_document_assets` |
| `document_relations` | 031 | （无接续，知识图谱模块整体退役） |
| `document_tags` / `tags` | 031 | （无接续） |
| `agent_traces` / `agent_trace_steps` | 032 | LangFuse OTLP（不再落库；见 [[features/langfuse-tracing]]） |
| `mcp_api_keys` | 017 | — |

完整迁移列表见 [[database/migrations]]。

## 通用规范

- **主键**：UUID (`uuid` 类型)，MyBatis `jdbcType=OTHER`
- **软删除**：`deleted_at TIMESTAMPTZ`，查询时 `AND deleted_at IS NULL`
- **时间字段**：`TIMESTAMPTZ`（带时区）
- **字段命名**：`snake_case`，Java 属性 `camelCase`，通过 `map-underscore-to-camel-case: true` 自动映射

## 实体详情

- [[database/entities/users-spaces]] — 用户、空间、权限
- [[database/entities/qa-sessions]] — QA 会话与消息
- [[database/entities/after-sales-tables]] — 售后审核、商城客服会话
