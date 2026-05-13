# ADR-003: 混合检索 + RRF 融合

**状态**：已采用

## 背景

纯语义搜索对精确关键词匹配效果差；纯关键词搜索对语义理解不足。

## 决策

并行运行语义检索（Milvus）和关键词检索（PostgreSQL pg_trgm），用 RRF（k=60）融合排序。

## 后果

- 综合效果优于单一模式
- 两路并行（CompletableFuture）不增加额外延迟
- PostgreSQL 需要 `pg_trgm` 扩展（migration 015）
- 存量数据中 Milvus ID 与 PG ID 不一致时，用内容指纹二次去重兜底
