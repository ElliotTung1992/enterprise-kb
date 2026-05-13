# 基础服务说明

## PostgreSQL 16

- 数据库名：`enterprise_kb`
- 数据持久化：`postgres_data` volume
- 初始化脚本：`docker/init/postgres-init.sql`
- 关键扩展：`pg_trgm`（migration 015 安装，用于全文检索）
- 连接池：HikariCP，max 20，idle 5

## Milvus 2.4 (Standalone)

- Collection：`kb_chunks`
- 维度：1536，Metric：COSINE，Index：IVF_FLAT
- 元数据：etcd
- 对象存储：MinIO
- GUI：Attu（http://localhost:3000）

> 切换 Embedding 提供商时，需手动删除 collection 并重建（`initialize-schema: true` 只会在首次不存在时创建）

## Redis 7

- 用途：QA 会话对话历史缓存
- Key 格式：`session:{sessionId}`
- TTL：24 小时
- maxmemory：256MB，策略：allkeys-lru
- 访问需密码：`REDIS_PASSWORD`

## MinIO

- 为 Milvus 提供 S3 兼容对象存储
- 管理控制台：http://localhost:9001
- 不存储业务文件（业务文件存 `uploads/` 目录 volume）

## 文件存储

业务文档存储在 Docker volume `uploads_data` → 容器内 `/app/uploads`。
本地开发路径由 `FILE_STORAGE_PATH` 环境变量配置（默认 `./uploads`）。
