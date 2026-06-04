# 🧠 Enterprise KB — 智能客服知识库

> 多模块 Spring Boot 项目，提供 Markdown 文档管理、混合检索、RAG 问答、客服助手与投诉升级能力。

---

## 快速导航

| 分类 | 链接 |
|------|------|
| 系统架构 | [[architecture/overview]] · [[architecture/module-dependency]] · [[architecture/data-flow]] |
| AI / RAG | [[ai-rag/hybrid-search]] · [[ai-rag/agentic-qa]] · [[ai-rag/providers]] · [[ai-rag/hitl-hook]] · [[ai-rag/session-memory]] |
| 数据库 | [[database/schema-overview]] · [[database/migrations]] |
| API 接口 | [[api/auth]] · [[api/users]] · [[api/spaces]] · [[api/documents]] · [[api/qa]] · [[api/after-sales]] · [[api/complaints]] · [[api/eval]] |
| 功能 | [[features/document-ingestion]] · [[features/markdown-structure-rag]] · [[features/markdown-image-rag]] · [[features/markdown-visual-rag-l2]] · [[features/md-keyword-bm25]] · [[features/hitl-after-sales]] · [[features/complaint-escalation]] · [[features/intent-routing]] · [[features/ragas-evaluation]] · [[features/langfuse-tracing]] · [[features/langfuse-tracing-boundary-refactor]] |
| 基础设施 | [[infrastructure/docker-compose]] · [[infrastructure/services]] |
| 架构决策 | [[decisions/adr-001-multi-module-maven]] · [[decisions/adr-002-milvus-vector-store]] · [[decisions/adr-003-hybrid-search-rrf]] · [[decisions/adr-004-agentic-rag]] · [[decisions/adr-005-hitl-transaction-ordering]] · [[decisions/adr-006-customer-assistant-separation]] · [[decisions/adr-007-complaint-escalation-stategraph]] · [[decisions/adr-008-intent-routing-two-tier]] · [[decisions/adr-009-agent-trace-foundation]] · [[decisions/adr-010-agent-trace-eval]] · [[decisions/adr-011-markdown-visual-rag-l2]] · [[decisions/adr-012-markdown-structure-rag]] · [[decisions/adr-013-md-keyword-bm25]] · [[decisions/adr-014-ragas-integration]] · [[decisions/adr-015-langfuse-tracing]] |

---

## 核心功能地图

```
用户上传 Markdown
    └─► MdDocumentIngestionWorker（virtual thread, ingestionExecutor）
            ├─ 结构切分：H1-H3 parent → 段落级 child（small-to-big）
            ├─ 表格双表示 / 代码块独立 / 图片 → MdImageUnderstandingService
            ├─ 向量化（DashScope text-embedding-v2, 1536 维, COSINE）
            └─ 写入 md_parent_chunk / md_child_chunk / md_document_asset (PG)
                + md_kb_chunks（Milvus, child 粒度）

用户提问
    └─► MdHybridSearchService（向量 + BM25/TRGM 关键词, RRF 融合, child 粒度）
            ├─ 可选 HyDE / 查询改写
            ├─ 可选 rerank（DashScope gte-rerank）
            ├─ Parent Expansion：topK child 折叠 parentId 后回查整段
            ├─ 标准 md QA：MdQnAServiceImpl（retrieve → expand → LLM）
            └─ Agentic md QA：MdAgenticQnAServiceImpl（ReactAgent + searchKnowledgeBase / readFullSection）

> 标准（非 Markdown）RAG 竖井于迁移 031 整体退役，当前唯一活跃竖井是 md 结构感知 RAG。
```

---

## 技术栈概览

| 层 | 技术 |
|----|------|
| 应用框架 | Spring Boot 3 · JDK 21 虚拟线程 |
| AI 框架 | Spring AI · Spring AI Alibaba |
| ORM | MyBatis + PageHelper |
| 向量数据库 | Milvus 2.4（COSINE, IVF_FLAT；活跃集合 `md_kb_chunks`） |
| 关系数据库 | PostgreSQL 16（`tensorchord/vchord-suite` 镜像）+ Liquibase |
| 缓存 | Redis 7（会话历史 · 客服路由状态 · ReactAgent checkpoint，TTL 24h） |
| AI 提供商 | DashScope（默认 Embedding）· llama.cpp 本地模型（默认 Chat） |
| LLM Tracing | Micrometer Observation → OTLP → 自部署 LangFuse（`--profile tracing` 可选启用） |

详见 [[architecture/tech-stack]]。

---

## 状态速览

- 活跃 RAG 竖井：md 结构感知 RAG（small-to-big 父子索引）+ md 关键词检索默认走 BM25（build 已跑通，可回退 TRGM）
- 商城客服：两层域路由 + StateGraph 投诉升级 + HITL 售后审核
- 评估 / 可观测：Ragas 离线评估 + LangFuse 在线 tracing（默认关，按 profile 启）

完整工作流归档见 [`.planning/`](../.planning/)。
