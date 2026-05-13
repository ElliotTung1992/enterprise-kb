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
| `JWT_SECRET` | JWT 密钥（≥32字符，`openssl rand -hex 32`） |
| `REDIS_PASSWORD` | Redis 密码 |
| `MINIMAX_API_KEY` | MiniMax API Key（默认 Chat 提供商） |

可选：`DASHSCOPE_API_KEY` · `OPENAI_API_KEY` · `ANTHROPIC_API_KEY`

## 服务依赖顺序

```
etcd ──┐
       ├──► milvus ──► attu
minio ─┘
postgres
redis
    所有健康 ──► app
```

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
