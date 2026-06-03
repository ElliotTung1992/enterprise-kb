---
created: 2026-05-23
tags: [adr, tombstone, superseded, rag, markdown, visual, minio]
---

# ADR-011：Markdown 图文 RAG L2 资产化摄取方案（已被取代）

**状态**：实现至 Phase 5，**已被取代**——本 ADR 基于已退役的标准竖井（`documents` / `document_chunks` / `kb_chunks` / `document_assets`）。随**迁移 031** 标准竖井整体退役后，图片处理理念并入 md 竖井的 `IMAGE_CAPTION` 链路，不再单独承载图文 RAG。L3 多模态向量保留为未来扩展。

> 当前活跃接续：[[features/markdown-image-rag]]。功能历史归档：[[features/markdown-visual-rag-l2]]。实施计划与沟通纪要：`.planning/2026-05-23-markdown-visual-rag-l2/`。

## 背景

标准摄取链路（`DocumentParserService` → `SentenceAwareChunkingService` → `document_chunks` + Milvus `kb_chunks`）是纯文本 RAG。Markdown 走 Tika 文本解析，对图片和流程图有明显信息损失：图片不 OCR、不生成 caption；Mermaid / PlantUML 只当源码文本；citation 无法引用"某张图"。企业知识库常见图文混排（操作截图、架构图、业务流程图），需要让图片 / 流程图也可检索、可引用。

## 决策

采用 **L2 资产化摄取**：图片 / 流程图**不直接以图片向量入库**，而是先转成可检索文本（`IMAGE_CAPTION` / `DIAGRAM_SUMMARY` 等 chunk）进入既有文本 embedding + Milvus 链路；原件 / 渲染图作为资产存储，用于展示、重跑、人工修正与 citation。是 L2，不是 L3 多模态检索。

关键选择（压缩自原 10 条）：

1. 新增 `document_assets` 作为视觉资产 source of truth；`document_chunks` + Milvus 为可重建投影。
2. 图片 blob / 渲染图存 MinIO，不进 PostgreSQL。
3. OCR / caption **异步**：正文先 `READY_WITH_PENDING_ASSETS`，Asset Worker 轮询补齐，不引入 MQ。
4. `VisualUnderstandingService` 统一抽象，OCR 与 caption provider 可替换（一期默认 Noop）。
5. 视觉 chunk 与正文同表（`document_chunks`）、同 collection（`kb_chunks`），靠 `content_type` 区分。
6. 图文关联用 `section` + `anchor_chunk_index` 隐式邻近（未来可升级为显式关系图）。
7. zip 作为 Markdown 资源包上传（一 zip 一主 `.md` + 同目录图片），带 zip-slip / 大小 / 数量安全校验。
8. Mermaid / PlantUML 渲染成资产。
9. 人工修正 → `REINDEX_PENDING` → worker 删旧 chunk / vector 重建。
10. Asset 级 citation：透传 `assetId` / `contentType` / `section` / `anchorChunkIndex`。

## 备选方案

- **方案 A 轻量 L2**（图片只留弱文本 alt / path）：信息损失大、无法回答图片内容——否决。
- **方案 B L3 多模态向量检索**（以图搜图）：显著放大模型 / 存储 / 检索融合 / 前端复杂度，一期不值——否决，**保留为未来扩展**。
- **方案 C 图片只存 MinIO、不入向量库**：图片彻底无法被检索命中——否决。

选 L2：在不动既有文本 RAG 基座的前提下，让图文可检索、可引用，复杂度可控。

## 影响

- 正面：图片 / 流程图可被 RAG 命中并引用；视觉理解失败不拖垮正文；provider 可插拔。
- 成本：新增资产表、异步 worker、状态机与重建逻辑。这些设计理念后被 md 竖井（[[features/markdown-image-rag]]）以更简形态沿用（`md_document_asset` + `IMAGE_CAPTION` child）。
