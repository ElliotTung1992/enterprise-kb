# 系统架构总览

## 整体分层

```
┌─────────────────────────────────────────────────┐
│              REST API (端口 8081)                 │
│  AuthController · SpaceController · DocumentController
│  SearchController · QnAController · TagController │
└─────────────────┬───────────────────────────────┘
                  │ Spring Security (JWT + 空间RBAC)
┌─────────────────▼───────────────────────────────┐
│                 Service 层                        │
│  DocumentIngestionPipeline (虚拟线程异步)          │
│  HybridSearchService · QnAService                │
│  AgenticQnAService (ReactAgent)                  │
└──────┬──────────┬──────────────┬────────────────┘
       │          │              │
┌──────▼──┐ ┌────▼──────┐ ┌────▼──────────┐
│ MyBatis │ │  Milvus   │ │ AI Providers  │
│  (PG)   │ │  Vector   │ │ DashScope     │
│         │ │  Store    │ │ MiniMax       │
└──────┬──┘ └───────────┘ │ OpenAI        │
       │                  │ Anthropic     │
┌──────▼──────────────────┴──────────────┐
│  PostgreSQL · Milvus · Redis · MinIO   │
└────────────────────────────────────────┘
```

## 关键设计决策

- **多模块 Maven** — 关注点分离，子模块间单向依赖，见 [[architecture/module-dependency]]
- **AppConfig 手动装配** — 解决跨模块循环依赖，`AuthService` 在 `kb-app` 中注册
- **虚拟线程** — JDK 21，文档摄入走 `ingestionExecutor`（virtual thread pool），不阻塞请求线程
- **双模式问答** — 标准 RAG 和 Agentic RAG（ReactAgent）共存，由接口路径区分
- **嵌入维度锁定** — 统一 1536 维，切换 Embedding 提供商需重建 Milvus collection

## 相关页面

- [[architecture/module-dependency]] — 模块依赖图
- [[architecture/data-flow]] — 请求数据流
- [[architecture/tech-stack]] — 技术选型详情
