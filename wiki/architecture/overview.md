# 系统架构总览

## 整体分层

```
┌─────────────────────────────────────────────────────────┐
│                  REST API（端口 8081）                    │
│  AuthController · SpaceController · UserController       │
│  MdDocumentController · MdQnAController · QnAController  │
│  CustomerAssistantController · ReviewController          │
│  ComplaintController · EvalRunController · EvalCaseCtrl  │
└─────────────────┬───────────────────────────────────────┘
                  │ Spring Security（JWT + 空间 RBAC）
┌─────────────────▼───────────────────────────────────────┐
│                     Service 层                            │
│  MdDocumentIngestionWorker（virtual thread, ingestionExecutor）│
│  MdHybridSearchService · MdQnAService · MdAgenticQnAService    │
│  CustomerAssistantService（域路由 + DomainHandler）            │
│  ComplaintEscalationService（StateGraph）                       │
└──────┬──────────┬──────────────┬───────────────────────┘
       │          │              │
┌──────▼──┐  ┌────▼──────┐  ┌────▼──────────┐
│ MyBatis │  │  Milvus   │  │ AI Providers  │
│  (PG)   │  │ md_kb_chunks │ DashScope     │
│         │  │ (Spring AI│  │ MiniMax       │
│         │  │ 自动配置  │  │ Anthropic     │
│         │  │ excluded) │  │               │
└──────┬──┘  └───────────┘  └───────────────┘
       │
┌──────▼──────────────────────────────────────┐
│  PostgreSQL · Milvus · Redis · MinIO         │
│  + ClickHouse / LangFuse（profile: tracing） │
└─────────────────────────────────────────────┘
```

## 关键设计决策

- **多模块 Maven** — 关注点分离，子模块间单向依赖。见 [[architecture/module-dependency]] · [[decisions/adr-001-multi-module-maven]]
- **AppConfig 手动装配** — 解决跨模块循环依赖；`AuthService`、`SpacePermissionEvaluator`、`ingestionExecutor`、`milvusClient` / `mdVectorStore` 均在 `kb-app/AppConfig.java` 手工声明
- **Spring AI Milvus 自动配置 excluded** — Milvus 自动配置会自建 `kb_chunks` 集合，但标准竖井已退役（迁移 031），所以 `spring.autoconfigure.exclude` 关掉它，由 `AppConfig` 手建 `mdVectorStore` 指向 `md_kb_chunks`
- **虚拟线程** — JDK 21，文档摄入走 `ingestionExecutor`（virtual thread pool），不阻塞请求线程
- **唯一 RAG 竖井：Markdown 结构感知 RAG** — small-to-big 父子索引；原标准（非 Markdown）竖井已随迁移 031 整体退役
- **客服助手两层域路由** — Tier-1 `DomainRouterService` 分类业务域，Tier-2 `DomainHandler` 选工具。kill-switch `enterprise.kb.customer-assistant.routing-enabled` 默认 false。见 [[features/intent-routing]] · [[decisions/adr-008-intent-routing-two-tier]]
- **嵌入维度锁定** — 统一 1536 维，切换 Embedding 提供商需重建 Milvus collection
- **在线 LLM tracing 可选装配** — `enterprise.kb.tracing.enabled` 默认关；开启时走 Micrometer Observation → OTLP → 自部署 LangFuse。见 [[features/langfuse-tracing]] · [[decisions/adr-015-langfuse-tracing]]

## 相关页面

- [[architecture/module-dependency]] — 模块依赖图
- [[architecture/data-flow]] — 请求数据流
- [[architecture/tech-stack]] — 技术选型详情
