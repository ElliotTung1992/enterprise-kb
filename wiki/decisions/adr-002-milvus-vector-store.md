---
created: 2026-04-01
tags: [adr, vector-store, milvus, embeddings]
---

# ADR-002: Milvus 作为向量数据库

**状态**：已采用（原 `kb_chunks` 集合已随迁移 031 退役，当前唯一活跃集合为 `md_kb_chunks`）

## 背景

需要高性能向量相似度搜索，支持大规模文档向量存储。

## 决策

使用 Milvus 2.4 Standalone 模式，通过 Spring AI `MilvusVectorStore` 集成。

- 当前活跃 Collection：`md_kb_chunks`（md 竖井 child chunk），1536维，COSINE，IVF_FLAT，**手工 bean** `mdVectorStore`（Spring AI 自动配置被 exclude）
- 历史 Collection：`kb_chunks`（标准 RAG 竖井），随迁移 031 退役
- Embedding 主提供商：DashScope text-embedding-v2

## 注意事项

- 维度统一锁定为 1536（兼容 DashScope 和 MiniMax embo-01）
- 切换 Embedding 提供商时，需重建 `md_kb_chunks` 并重新摄入所有 md 文档
- `mdVectorStore` 是手工 bean，需注入 `ObservationRegistry` 才有 VectorStore 自动 span（见 [[features/langfuse-tracing]] 埋点盲区 D）
