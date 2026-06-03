# Markdown 图文 RAG L2 方案（历史归档）

> [!stale] 已被取代——保留作历史架构记录
> 本方案是**在已退役的标准竖井**（`documents` / `document_chunks` / `kb_chunks` / `document_assets`）之上做的 zip 图文 RAG，实现至 Phase 5。随**迁移 031** 标准竖井整体退役后，其图片处理理念被并入 md 竖井的 `IMAGE_CAPTION` 链路。
> **当前活跃接续**：[[features/markdown-image-rag]]（md 竖井图片支持）。
> 架构决策：[[decisions/adr-011-markdown-visual-rag-l2]]（已精简为决策墓碑）。
> 实施计划与逐字沟通纪要：`.planning/2026-05-23-markdown-visual-rag-l2/`（task_plan / findings / progress）。

本页把原"功能方案 + 沟通纪要"两篇合并为一份精炼的历史记录，仅保留值得复用的设计理念，删去依赖已退役表结构的字段级细节。

## 这是什么

让 Markdown 文档里的**正文、图片、流程图**都进入 RAG 检索链路，并在问答时引用到具体图片或渲染后的流程图。它是 **L2 图文处理**：把图片/流程图转成文本说明后走文本向量检索，**不引入图片向量 / 多模态向量**（那是预留的 L3）。第一版以 **zip 上传**为容器（一个 zip 内一个主 `.md` + 同目录图片），带 zip-slip / 大小 / 数量 / 单图体积等安全校验。

## 值得复用的设计理念

- **三存储分工**：`document_assets` 是视觉资产的 source of truth；`document_chunks` + Milvus 是可重建的检索投影；MinIO 存二进制（原图 / 渲染图 / 原始 zip）。这个"主数据 vs 可重建投影"的心智模型被 md 竖井沿用（`md_document_asset` + `md_kb_chunks`）。
- **同步正文 + 异步视觉**：正文与图片引用先入库让文档尽快可检索，OCR / caption 由 Asset Worker 异步轮询补齐，失败不拖垮正文。文档状态因此细分出 `READY_WITH_PENDING_ASSETS` / `READY_WITH_ASSET_ERRORS`。
- **`content_type` 分类**：`TEXT` / `IMAGE_REFERENCE` / `IMAGE_CAPTION` / `DIAGRAM_SOURCE` / `DIAGRAM_SUMMARY`，让一条 chunk 自带"内容形态"。md 竖井简化保留了 `TEXT` / `IMAGE_CAPTION`。
- **Noop 视觉 provider**：先落 `VisualUnderstandingService` 抽象 + 默认 Noop 实现，真实 OCR / caption / VLM 可在接口后替换——与 md 竖井 `MdImageUnderstandingService`（默认 Noop，可切 DashScope）是同一思路。
- **人工修正 + 重建索引**：`manual_caption` / `manual_summary` 写入后把资产置 `REINDEX_PENDING`，worker 删旧 chunk/vector 再重建。
- **资产级引用**：citation 透传 `assetId` / `contentType` / `section` / `anchorChunkIndex`，问答按 `assetId` 合并视觉引用、优先保留 `IMAGE_CAPTION` / `DIAGRAM_SUMMARY`。

## 为什么被取代

迁移 031 把标准竖井整套表（`documents` / `document_chunks` / `kb_chunks` / `document_assets`）全部退役，本方案的落地基座随之消失。图片支持改在**结构感知 md 竖井**上重做：固定 MinIO URL 解析、同步视觉理解、`IMAGE_CAPTION` 类型 child、`md_document_asset` 资产表——即 [[features/markdown-image-rag]]。

## 保留的未来扩展点

- **L3 多模态向量检索**：以图搜图、图片区域级引用，本方案明确列为非目标，留作未来扩展。
- 显式图文关系图（`document_chunk_relations`：source/target + relation_type），替代当前"`documentId` + `section` + `anchorChunkIndex`"的隐式邻近关联。

## 关联

- 当前活跃实现：[[features/markdown-image-rag]] · [[features/markdown-structure-rag]]
- 决策墓碑：[[decisions/adr-011-markdown-visual-rag-l2]]
- 计划 / 纪要存档：`.planning/2026-05-23-markdown-visual-rag-l2/`
