# ADR-002: Milvus 作为向量数据库

**状态**：已采用

## 背景

需要高性能向量相似度搜索，支持大规模文档向量存储。

## 决策

使用 Milvus 2.4 Standalone 模式，通过 Spring AI `MilvusVectorStore` 集成。

- Collection：`kb_chunks`，1536维，COSINE，IVF_FLAT
- Embedding 主提供商：DashScope text-embedding-v2

## 注意事项

- 维度统一锁定为 1536（兼容 DashScope 和 MiniMax embo-01）
- 切换 Embedding 提供商时，需重建 collection 并重新摄入所有文档
- `initialize-schema: true` 只在 collection 不存在时创建，不会自动迁移结构
