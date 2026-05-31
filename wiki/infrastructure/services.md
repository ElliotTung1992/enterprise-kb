# 基础服务说明

## PostgreSQL 16

- 数据库名：`enterprise_kb`
- 数据持久化：`postgres_data` volume
- 初始化脚本：`docker/init/postgres-init.sql`（建主库 + LangFuse 独立库）
- 关键扩展：
  - `pg_trgm`（迁移 015 安装，全文检索 GIN 索引）
  - `pg_tokenizer` + `vchord_bm25`（迁移 029 安装，仅 `tensorchord/vchord-suite` 镜像可用；详见 [[features/md-keyword-bm25]]）
- 连接池：HikariCP，max 20，idle 5

## Milvus 2.4（Standalone）

- 当前活跃 Collection：`md_kb_chunks`（md 竖井 child chunk）
- 维度：1536，Metric：COSINE，Index：IVF_FLAT
- 手工 bean：`mdVectorStore`（Spring AI Milvus 自动配置被 exclude）
- 元数据：etcd
- 对象存储：MinIO
- GUI：Attu（http://localhost:3000）

> 切换 Embedding 提供商时，需手动删除 `md_kb_chunks` 集合并重建（`initialize-schema: true` 只会在首次不存在时创建）。
>
> 原标准竖井 collection `kb_chunks` 已随迁移 031 退役。

## Redis 7

- 用途：
  - QA 会话对话历史缓存（`session:{sessionId}`，TTL 24h）
  - 客服助手会话状态（`qa:state:{sessionId}`，意图路由 awaiting_slot 等）
  - ReactAgent checkpoint（HITL 暂停 / 恢复用）
  - LangFuse worker 独立 DB index（仅 tracing profile 开启时）
- TTL：24 小时
- maxmemory：256MB，策略：allkeys-lru
- 访问需密码：`REDIS_PASSWORD`

## MinIO

- 为 Milvus 提供 S3 兼容对象存储
- 业务用 bucket：`md-documents`（原始 .md 文件 + 图片资产）
- LangFuse 独立 bucket：`langfuse`（**需预先创建**，仅 tracing profile 开启时）
- 管理控制台：http://localhost:9001

## ClickHouse（profile: tracing）

- LangFuse trace 数据，**30 天 TTL**（可配）
- 仅 `docker compose --profile tracing up -d` 时启动
- 必需密钥：`CLICKHOUSE_PASSWORD`

## LangFuse（profile: tracing）

- `langfuse-web`（宿主 3001，attu 占用 3000）+ `langfuse-worker`
- 复用 PG（独立库 `langfuse`）/ Redis（独立 DB index）/ MinIO（独立 bucket）
- 必需密钥：`LANGFUSE_SALT` / `LANGFUSE_ENCRYPTION_KEY` / `LANGFUSE_NEXTAUTH_SECRET`（禁止使用 compose 示例默认值）
- 详见 [[features/langfuse-tracing]]
