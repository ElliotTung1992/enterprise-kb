---
created: 2026-04-01
tags: [adr, search, hybrid, rrf]
---

# ADR-003: 混合检索 + RRF 融合

**状态**：已采用（原 `HybridSearchService` 随迁移 031 退役，当前活跃实现为 `MdHybridSearchService`，融合策略不变）

## 背景

纯语义检索对精确关键词匹配效果差；纯关键词检索对语义理解不足。

## 决策

并行运行语义检索（Milvus）和关键词检索（PostgreSQL），用 RRF（k=60）融合排序。

## 后果

- 综合效果优于单一模式
- 两路并行（`CompletableFuture` 或 reactor）不增加额外延迟
- 当前 md 竖井：`MdHybridSearchService`，关键词路 pg_trgm 或 BM25（VectorChord-bm25 + jieba，见 [[features/md-keyword-bm25]]），融合阶段 child 粒度**不去重**，保留多 child 位置供多窗节选 + parent expansion
- RRF 对分数符号无感（按排名融合）—— BM25 负分免归一化
- 历史标准竖井曾用内容指纹做 Milvus ID ↔ PG ID 二次去重，已随迁移 031 退役
