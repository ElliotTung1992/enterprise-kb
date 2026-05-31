# 技术选型

## 核心依赖

| 依赖 | 用途 |
|------|------|
| Spring Boot 3 | 应用框架（JDK 21 虚拟线程） |
| Spring AI | AI 抽象层（`ChatClient`, `EmbeddingModel`, `VectorStore`, Observation） |
| Spring AI Alibaba | DashScope · `ReactAgent` · StateGraph · `ToolInterceptor` / `GraphObservationLifecycleListener` |
| MyBatis 3.0.3 | ORM，SQL 写在 mapper XML |
| PageHelper 2.1.0 | 分页插件 |
| Liquibase | 数据库版本管理（随 Spring Boot 自动执行） |
| JJWT | JWT 签发 / 校验 |
| Lombok | `@Getter` / `@Setter` / `@Slf4j` / `@RequiredArgsConstructor` |
| JTokkit | Token 计数（Agentic token budget） |
| Micrometer Tracing → OTel | LLM tracing（`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`，仅 tracing profile 启用） |
| Ragas（Python service / SDK） | 离线 LLM-as-judge 评估 |

## AI 提供商

| 提供商 | ChatModel | EmbeddingModel | 说明 |
|--------|-----------|----------------|------|
| DashScope（阿里云） | qwen-plus | text-embedding-v2（1536 维） | 默认 Embedding，`@Primary` |
| MiniMax | MiniMax-M2.7-highspeed | embo-01（1536 维） | 默认 Chat（路由器 Tier-1 也用此） |
| Anthropic | claude-sonnet-4-5 | — | 可选，仅 Chat |

> 维度统一 1536，切换 Embedding 提供商需重建 `md_kb_chunks` 并重新摄入。

## 基础设施

### 默认 profile

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL 16 | `tensorchord/vchord-suite:pg16-...` | 5432 | 主库 + `pg_trgm` + `pg_tokenizer` + `vchord_bm25`（迁移 029 装） |
| Milvus 2.4 | `milvusdb/milvus:v2.4.8` | 19530 | 向量存储（活跃集合 `md_kb_chunks`） |
| Redis 7 | `redis:7-alpine` | 6379 | 会话历史 / 客服路由状态 / ReactAgent checkpoint |
| MinIO | `minio/minio` | 9000 / 9001 | Milvus 对象存储 + 业务 bucket `md-documents` |
| etcd | `quay.io/coreos/etcd:v3.5.14` | 2379 | Milvus 元数据 |
| Attu | `zilliz/attu:v2.4` | 3000 | Milvus GUI |

### `--profile tracing` 增量（[[features/langfuse-tracing]]）

| 服务 | 用途 |
|------|------|
| `langfuse-web` | 宿主 **3001**（避开 Attu 3000）；OTLP traces 入口 `/api/public/otel/v1/traces` |
| `langfuse-worker` | ClickHouse / S3 写入 |
| `clickhouse` | LangFuse trace 存储，**30d TTL** |

LangFuse 复用现有 PG（独立库 `langfuse`，由 `postgres-init.sql` 建）/ Redis（独立 DB index）/ MinIO（独立 bucket `langfuse`，需预先创建）。必需密钥：`LANGFUSE_SALT` / `LANGFUSE_ENCRYPTION_KEY` / `LANGFUSE_NEXTAUTH_SECRET` / `CLICKHOUSE_PASSWORD`。

## 相关页面

- [[architecture/overview]] — 整体分层
- [[infrastructure/docker-compose]] — compose 启动方式
- [[infrastructure/services]] — 各服务运行时配置
- [[features/langfuse-tracing]] — tracing profile 完整方案
