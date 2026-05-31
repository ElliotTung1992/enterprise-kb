# 混合检索（Hybrid Search）

## 原理

混合检索并行执行两路搜索，用 **RRF（Reciprocal Rank Fusion）** 算法融合结果。当前活跃实现是 `MdHybridSearchService`（md 竖井，唯一）；原标准竖井 `HybridSearchService` 随迁移 031 退役。

```
查询
 ├─► MdVectorSearchService    → Milvus 向量检索（md_kb_chunks, COSINE）
 └─► MdKeywordSearchService   → PostgreSQL 关键词检索
         · 默认 pg_trgm（md_child_chunk.embed_text 上 GIN 索引）
         · 可切到 BM25（VectorChord-bm25 + pg_tokenizer jieba，见 [[features/md-keyword-bm25]]）
        │
        ▼
   RRF 融合（k=60，child 粒度，融合阶段不去重）
   score(d) = Σ 1/(k + rank_i(d))
        │
        ▼
   [可选] RerankService（DashScope gte-rerank 精排）
        │
        ▼
   [可选] MdParentExpansionService（small-to-big：topK child 折叠 parentId → 回查整段 parent + 多窗节选）
        │
        ▼
   TopK 结果（child + 完整 parent 正文）
```

## RRF 参数

- `k = 60`（固定值，在 `MdHybridSearchServiceImpl` 中）
- 按排名融合，**对分数符号无感** —— BM25 负分免归一化
- 融合阶段 **不去重**，保留多 child 位置供多窗节选与 parent expansion

## 关键实现

- `MdHybridSearchService` / `MdHybridSearchServiceImpl`
- 两路搜索用 `CompletableFuture` 并行（如开 LangFuse tracing，executor 需经 `ContextSnapshot.wrapExecutor` 包，否则两段检索 span 脱根——见 [[features/langfuse-tracing]]）
- `MdVectorSearchService` 依赖 `mdVectorStore` 手工 bean（主 EmbeddingModel: DashScope）
- `MdKeywordSearchService` 走 JdbcTemplate + `similarity(?, col)` + `ILIKE`（默认 TRGM）或 `to_bm25query(...)` 倒排索引（BM25 模式）

## 可选增强

| 特性 | 类 | 说明 |
|------|-----|------|
| HyDE | `HydeService` | 用 LLM 先生成假设答案，再用假设答案向量检索 |
| 查询改写 | `QueryRewriteService` | 多路查询扩展，提升召回率 |
| 精排 | `RerankService` | DashScope gte-rerank，child 粒度精排 |
| Parent 展开 | `MdParentExpansionService` | small-to-big 回查整段 parent + 多窗节选 |

## 历史

原标准竖井（`document_chunks` + Milvus `kb_chunks`，关键词走 pg_trgm）已随迁移 031 退役。`MdHybridSearchService` 是当前唯一活跃形态，融合策略不变。BM25 升级仅 md 竖井做（[[features/md-keyword-bm25]]）。

## 相关页面

- [[ai-rag/agentic-qa]] — Agentic RAG 把混合检索包成 `searchKnowledgeBase` 工具
- [[features/markdown-structure-rag]] — md 竖井 small-to-big 父子索引
- [[features/md-keyword-bm25]] — 关键词路 BM25 升级
- [[features/langfuse-tracing]] — 检索四段 span（vector/keyword/rerank/parent_expansion）
- [[decisions/adr-003-hybrid-search-rrf]] — RRF 融合决策
