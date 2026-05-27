# 🧠 Enterprise KB — 智能客服知识库

> 多模块 Spring Boot 项目，提供文档管理、语义检索、RAG 问答和知识图谱能力。

---

## 快速导航

| 分类 | 链接 |
|------|------|
| 系统架构 | [[architecture/overview]] · [[architecture/module-dependency]] · [[architecture/data-flow]] |
| 模块详情 | [[modules/kb-document]] · [[modules/kb-search]] · [[modules/kb-knowledge-graph]] |
| AI / RAG | [[ai-rag/hybrid-search]] · [[ai-rag/agentic-qa]] · [[ai-rag/providers]] · [[ai-rag/hitl-hook]] |
| 数据库 | [[database/schema-overview]] · [[database/migrations]] |
| API 接口 | [[api/qa]] · [[api/documents]] · [[api/search]] · [[api/after-sales]] |
| 功能 | [[features/document-ingestion]] · [[features/markdown-visual-rag-l2]] · [[features/markdown-structure-rag]] · [[features/knowledge-graph]] · [[features/hitl-after-sales]] · [[features/complaint-escalation]] · [[features/intent-routing]] |
| 基础设施 | [[infrastructure/docker-compose]] · [[infrastructure/services]] |
| 架构决策 | [[decisions/adr-001-multi-module-maven]] · [[decisions/adr-005-hitl-transaction-ordering]] · [[decisions/adr-006-customer-assistant-separation]] · [[decisions/adr-007-complaint-escalation-stategraph]] · [[decisions/adr-008-intent-routing-two-tier]] · [[decisions/adr-011-markdown-visual-rag-l2]] · [[decisions/adr-012-markdown-structure-rag]] |

---

## 核心功能地图

```
用户上传文档
    └─► DocumentIngestionPipeline
            ├─ 解析 (PDF/Word/MD/TXT)
            ├─ 分块 (512 token, 100 overlap)
            ├─ 向量化 (DashScope text-embedding-v2, 1536维)
            └─ 存入 Milvus + PostgreSQL

用户提问
    └─► HybridSearch (语义 + 关键词 RRF融合)
            ├─ 标准 QA: 检索 → Rerank → LLM
            └─ Agentic QA: ReactAgent 多轮工具调用
```

---

## 技术栈概览

| 层 | 技术 |
|----|------|
| 应用框架 | Spring Boot 3 · JDK 21 虚拟线程 |
| AI 框架 | Spring AI · Spring AI Alibaba |
| ORM | MyBatis + PageHelper |
| 向量数据库 | Milvus 2.4 (COSINE, IVF_FLAT) |
| 关系数据库 | PostgreSQL 16 + Liquibase |
| 缓存 | Redis 7 (会话历史, TTL 24h) |
| AI 提供商 | DashScope · MiniMax · OpenAI · Anthropic |

---

## 最近更新

- [ ] 待补充：最新功能变更
- 参见 `git log --oneline -10`
