# 数据库 Schema 总览

数据库：PostgreSQL 16，数据库名：`enterprise_kb`
Schema 通过 **Liquibase** 管理，changelog: `kb-app/src/main/resources/db/changelog/`

## 表清单

| 迁移文件 | 表名 | 说明 |
|---------|------|------|
| 001 | `roles` | 系统角色定义 |
| 002 | `users` | 用户账户 (BCrypt 密码) |
| 003 | `spaces` | 知识空间 |
| 004 | `user_space_roles` | 用户-空间-角色三元组 |
| 005 | `documents` | 文档元数据 |
| 006 | `document_chunks` | 文档分块元数据 |
| 007 | `tags` | 标签树 |
| 008 | `document_tags` | 文档-标签关联 |
| 009 | `document_relations` | 文档关系图 |
| 010 | `refresh_tokens` | JWT RefreshToken |
| 011 | `audit_logs` | 操作审计日志 |
| 012 | (default data) | 初始角色等默认数据 |
| 013 | `mcp_api_keys` | (已在017删除) |
| 014 | (drop FK) | 降低约束，提升摄入性能 |
| 015 | (trgm) | 安装 pg_trgm 扩展，建全文索引 |
| 016 | `qa_sessions` + `qa_messages` | QA 会话持久化 |
| 017 | (drop mcp_api_keys) | 删除 MCP API Key 表 |
| 018 | `review_requests` | HITL 售后审核申请（含对话快照、订单详情 JSONB） |
| 019 | `customer_sessions` + `customer_messages` | 商城客服对话会话与消息 |
| 020 | (alter review_requests) | `space_id` 改为可空（客户助手无知识空间） |

## 通用规范

- **主键**：UUID (`uuid` 类型)，MyBatis `jdbcType=OTHER`
- **软删除**：`deleted_at TIMESTAMPTZ`，查询时 `AND deleted_at IS NULL`
- **时间字段**：`TIMESTAMPTZ`（带时区）
- **字段命名**：`snake_case`，Java 属性 `camelCase`，通过 `map-underscore-to-camel-case: true` 自动映射

## 实体详情

- [[database/entities/users-spaces]] — 用户、空间、权限
- [[database/entities/documents-chunks]] — 文档、分块
- [[database/entities/qa-sessions]] — QA 会话与消息
- [[database/entities/tags-graph]] — 标签与知识图谱
- [[database/entities/after-sales-tables]] — 售后审核、商城客服会话
