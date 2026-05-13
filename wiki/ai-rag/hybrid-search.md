# 混合检索 (Hybrid Search)

## 原理

混合检索并行执行两路搜索，用 **RRF（Reciprocal Rank Fusion）** 算法融合结果。

```
查询
 ├─► SemanticSearchService  → Milvus 向量检索 (COSINE 相似度)
 └─► KeywordSearchService   → PostgreSQL pg_trgm 全文检索
        │
        ▼
   RRF 融合 (k=60)
   score(d) = Σ 1/(k + rank_i(d))
        │
        ▼
   [可选] RerankService (DashScope gte-rerank 精排)
        │
        ▼
   TopK 结果
```

## RRF 参数

- `k = 60`（固定值，在 `HybridSearchServiceImpl` 中）
- 内容指纹去重：兜底 Milvus ID 与 PostgreSQL ID 不一致的情况

## 关键实现

- `HybridSearchService` / `HybridSearchServiceImpl`
- 两路搜索用 `CompletableFuture` 并行执行
- `SemanticSearchService` 依赖 `MilvusVectorStore`（主 EmbeddingModel: DashScope）
- `KeywordSearchService` 依赖 PostgreSQL `pg_trgm` 扩展（migration 015 安装）

## 可选增强

| 特性 | 类 | 说明 |
|------|-----|------|
| HyDE | `HydeService` | 用 LLM 先生成假设答案，再用假设答案向量检索 |
| 查询改写 | `QueryRewriteService` | 多路查询扩展，提升召回率 |
| 精排 | `RerankService` | DashScope gte-rerank，对初步检索结果重新排序 |

## 相关页面

- [[ai-rag/agentic-qa]] — Agentic RAG 如何调用混合检索作为工具
- [[database/entities/documents-chunks]] — document_chunks 表结构
