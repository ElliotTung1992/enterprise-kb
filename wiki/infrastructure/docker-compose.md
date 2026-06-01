# Docker Compose

## 快速启动

```bash
cp .env.example .env    # 填写必填项
docker compose --env-file .env up -d
curl http://127.0.0.1:8081/actuator/health
```

## 必填环境变量

| 变量 | 说明 |
|------|------|
| `PG_PASSWORD` | PostgreSQL 密码 |
| `JWT_SECRET` | JWT 密钥（≥32 字符，`openssl rand -hex 32`） |
| `REDIS_PASSWORD` | Redis 密码 |
| `LLAMA_CPP_BASE_URL` / `LLAMA_CPP_MODEL` / `LLAMA_CPP_API_KEY` | llama.cpp 本地模型（默认 Chat 提供商 + 路由器 Tier-1，OpenAI 兼容接口） |
| `DASHSCOPE_API_KEY` | DashScope API Key（默认 Embedding 提供商；rerank / 图片理解也用此） |

可选：`ANTHROPIC_API_KEY`

## `--profile tracing`（可选，LangFuse 在线 LLM tracing）

```bash
docker compose --profile tracing --env-file .env up -d
```

启动额外 3 个容器：`langfuse-web`（宿主 3001）/ `langfuse-worker` / `clickhouse`。复用现有 PG / Redis / MinIO 实例（独立库 / index / bucket）。

必填增量环境变量：

| 变量 | 说明 |
|------|------|
| `KB_TRACING_ENABLED=true` | 应用侧总开关（不开则不连 OTLP） |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://langfuse-web:3000/api/public/otel/v1/traces` |
| `LANGFUSE_OTLP_BASIC_AUTH` | `base64(pk:sk)` |
| `LANGFUSE_SALT` / `LANGFUSE_ENCRYPTION_KEY` / `LANGFUSE_NEXTAUTH_SECRET` | LangFuse web/worker 必需密钥（**禁止使用 compose 示例默认值**） |
| `CLICKHOUSE_PASSWORD` | ClickHouse 密码 |

详见 [[features/langfuse-tracing]] · [[decisions/adr-015-langfuse-tracing]]。

## 服务依赖顺序（默认 profile）

```
etcd ──┐
       ├──► milvus ──► attu
minio ─┘
postgres（vchord-suite 镜像，含 pg_trgm / pg_tokenizer / vchord_bm25）
redis
    所有健康 ──► app（端口 8081）
```

`--profile tracing` 启动时额外：`postgres` + `redis` + `minio` → `clickhouse` → `langfuse-worker` + `langfuse-web`（端口 3001）。

## 常用命令

```bash
# 查看日志
docker compose logs -f app
docker compose logs -f milvus

# 重启单个服务
docker compose restart app

# 进入 PostgreSQL
docker compose exec postgres psql -U kb_user -d enterprise_kb

# 查看 Milvus GUI
open http://localhost:3000
```

## 生产部署

使用 `docker-compose.prod.yml`（精简版，无 Attu 等开发工具）：
```bash
docker compose -f docker-compose.prod.yml --env-file .env up -d
```
