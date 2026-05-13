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

## 注意事项

- 迁移文件**不可修改**（Liquibase 用 checksum 校验）
- 需要改已有表，必须新建迁移文件
- 生产环境数据库密码通过 `PG_PASSWORD` 环境变量注入
