# 技术选型

## 核心依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.x | 应用框架 |
| Spring AI | 最新 | AI 抽象层 (ChatClient, EmbeddingModel, VectorStore) |
| Spring AI Alibaba | 最新 | DashScope · ReactAgent 支持 |
| MyBatis | 3.0.3 | ORM，SQL 写在 mapper XML |
| PageHelper | 2.1.0 | 分页插件 |
| Liquibase | 随 Spring Boot | 数据库版本管理 |
| JJWT | 最新 | JWT 签发/校验 |
| Lombok | 最新 | @Getter/@Setter/@Slf4j/@RequiredArgsConstructor |
| JTokkit | 最新 | Token 计数 (Agentic token budget) |

## AI 提供商

| 提供商 | ChatModel | EmbeddingModel | 说明 |
|--------|-----------|----------------|------|
| DashScope (阿里云) | qwen-plus | text-embedding-v2 (1536维) | 默认 Embedding，`@Primary` |
| MiniMax | MiniMax-M2.7-highspeed | embo-01 (1536维) | 默认 Chat |
| OpenAI | gpt-* | text-embedding-3-small | 可选，需配置 OPENAI_API_KEY |
| Anthropic | claude-sonnet-4-5 | — | 可选，仅 Chat |

## 基础设施

| 服务 | 镜像 | 端口 | 用途 |
|------|------|------|------|
| PostgreSQL 16 | postgres:16 | 5432 | 主数据库 |
| Milvus 2.4 | milvusdb/milvus:v2.4.8 | 19530 | 向量存储 |
| Redis 7 | redis:7-alpine | 6379 | 会话历史缓存 |
| MinIO | minio/minio | 9000/9001 | Milvus 对象存储 |
| etcd | quay.io/coreos/etcd:v3.5.14 | 2379 | Milvus 元数据 |
| Attu | zilliz/attu:v2.4 | 3000 | Milvus GUI |
