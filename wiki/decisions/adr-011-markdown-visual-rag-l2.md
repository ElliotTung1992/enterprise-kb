---
created: 2026-05-23
tags: [adr, architecture, rag, document-ingestion, markdown, visual, minio]
---

# ADR-011：Markdown 图文 RAG L2 资产化摄取方案

**状态**：实现至 Phase 5；**本 ADR 基于已退役的标准竖井设计**（`documents` / `document_chunks` / `kb_chunks`），随迁移 031 退役后，相关图片处理理念被并入 md 竖井的 IMAGE_CAPTION 链路（[[features/markdown-structure-rag]] + [.planning/2026-05-27-markdown-image-rag/](#)）；不再单独承载图文 RAG。L3 多模态向量保留为未来扩展。

> 详细功能设计见 [[features/markdown-visual-rag-l2]]。沟通纪要与未来优化点见 [[features/markdown-visual-rag-l2-design-notes]]。实施计划见 `.planning/2026-05-23-markdown-visual-rag-l2/`。

## 背景

当前文档摄取链路以文本 RAG 为核心：

```text
DocumentParserService.parse()
  -> SentenceAwareChunkingService.chunk()
  -> VectorStoreService.upsert()
  -> ChunkMetadataService.saveChunks()
  -> document_chunks + Milvus kb_chunks
```

Markdown 虽然已经在上传白名单中，但当前解析方式主要走 Tika 文本解析。对于 Markdown 中的图片和流程图，存在明显信息损失：

- Markdown 图片本体不会 OCR，也不会生成 caption。
- 图片最多可能保留 alt/path 这类弱文本，无法回答图片内容。
- Mermaid / PlantUML 代码块可能作为普通源码文本进入 RAG，但没有资产化、渲染图、图说明或引用能力。
- RAG citation 当前只能引用文本 chunk，无法明确引用“某张图”。

系统需要支持企业知识库中常见的图文混排 Markdown：

- 产品操作手册截图。
- 架构图。
- 业务流程图。
- Mermaid / PlantUML 流程图。
- 图文相邻解释段落。

同时，项目当前 RAG 基础设施已经围绕文本 chunk、PostgreSQL 和 Milvus 构建；直接引入 L3 多模态图片向量检索会明显扩大模型、存储、检索融合和前端引用复杂度。

## 决策

采用 **Markdown 图文 RAG L2 资产化摄取方案**：

```text
图片/流程图原件或渲染图 -> MinIO
图片/流程图元数据与处理状态 -> document_assets
OCR/caption/summary -> document_assets
图片/流程图语义文本 -> document_chunks
图片/流程图语义向量 -> 现有 Milvus collection
```

即：

- 保持现有文本 embedding 和 Milvus 检索链路。
- 图片和流程图不直接以图片向量入库。
- 图片和流程图先转成可检索文本，再作为 `IMAGE_CAPTION` / `DIAGRAM_SUMMARY` 等 chunk 进入现有 RAG。
- 原图和渲染图作为资产存储，用于展示、重跑 OCR/caption、人工修正和 citation。

本方案是 **L2 图文处理**，不是 L3 多模态检索。

## 关键决策

### 1. 新增 `document_assets`

新增视觉资产表，作为图片/流程图的 source of truth。

主要职责：

- 保存 MinIO object key。
- 保存 Markdown 原始路径、alt、title、section。
- 保存 Mermaid / PlantUML 源码。
- 保存 OCR/caption/summary。
- 保存处理状态、重试次数、错误信息。
- 保存人工修正字段。
- 关联视觉 chunk 与 RAG citation。

`document_chunks` 和 Milvus 是可重建的检索投影，不作为视觉资产主数据。

### 2. 图片二进制和渲染图存 MinIO

不把图片 blob 存 PostgreSQL。

建议 object key：

```text
documents/{spaceId}/{documentId}/source/original.zip
documents/{spaceId}/{documentId}/source/main.md
documents/{spaceId}/{documentId}/assets/{assetId}/original.png
documents/{spaceId}/{documentId}/assets/{assetId}/thumbnail.webp
documents/{spaceId}/{documentId}/assets/{assetId}/rendered.svg
```

### 3. 异步 OCR/caption

视觉理解不阻塞正文 RAG。

文档正文摄取完成后可以先进入：

```text
READY_WITH_PENDING_ASSETS
```

后台 asset worker 通过数据库轮询处理 `PENDING` / `REINDEX_PENDING` 资产。

原因：

- 外部 OCR/caption 模型可能慢或失败。
- 用户应能先检索正文。
- 服务重启后可通过数据库状态恢复处理。
- 暂不引入 MQ，降低一期基础设施复杂度。

### 4. OCR 和 caption 分开抽象

业务层统一暴露：

```text
VisualUnderstandingService
```

内部拆分：

```text
OCRService
CaptionService / VLM Provider
```

原因：

- OCR 更适合精确文字识别。
- VLM/caption 更适合理解图片语义。
- 两者可独立配置超时、并发、重试和替换 provider。

### 5. 同表同 collection

视觉说明文本进入现有：

```text
document_chunks
Milvus collection: kb_chunks
```

新增 chunk 类型：

```text
TEXT
IMAGE_REFERENCE
IMAGE_CAPTION
DIAGRAM_SOURCE
DIAGRAM_SUMMARY
```

Milvus metadata 同步包含：

```json
{
  "documentId": "...",
  "contentType": "IMAGE_CAPTION",
  "assetId": "...",
  "assetType": "IMAGE",
  "section": "退款流程"
}
```

原因：

- L2 的检索对象仍然是文本。
- 可复用现有 Hybrid Search、Rerank、RAG prompt 和 citation 管线。
- 避免第一版引入多 collection 融合检索。

### 6. 图文关联使用 section + anchor chunk index

第一版不用显式关系图表。

采用：

```text
documentId + section + anchor_chunk_index
```

检索扩展规则：

- 命中图片/流程图 chunk 时，补充同文档、同章节、anchor 附近正文 chunk。
- 命中正文 chunk 时，可补充同文档、同章节、anchor 接近的视觉 chunk。
- 多个命中属于同一 `asset_id` 时，在 citation 层合并。

未来如有需要，可新增：

```text
document_chunk_relations
```

用于显式图关系。

### 7. Zip 作为 Markdown 资源包上传

第一版支持 Markdown zip：

- zip 是上传容器。
- `documents` 记录代表 zip 内唯一主 Markdown。
- `documents.mime_type = text/markdown`。
- zip 中第一版只允许一个主 `.md`。
- 原始 zip 存 MinIO。
- 临时解压目录处理完成后删除。

安全边界：

- 禁止 zip slip：`../` 和绝对路径。
- 限制总解压大小。
- 限制文件数。
- 限制图片大小和图片数量。
- 第一版只支持 Markdown 同目录或子目录的安全相对图片路径。
- 禁止远程 URL、绝对路径和 base64 data URL。

### 8. Mermaid / PlantUML 渲染成资产

Markdown 中的 Mermaid / PlantUML：

- 保存源码。
- 尝试渲染成 SVG/PNG 后存 MinIO。
- 生成 `DIAGRAM_SOURCE` chunk。
- 视觉理解完成后生成 `DIAGRAM_SUMMARY` chunk。
- 渲染失败不阻断正文摄取，记录错误并保留源码说明。

### 9. 人工修正与异步重建

支持人工修正：

```text
manual_caption
manual_summary
```

保存修正后：

```text
asset.status = REINDEX_PENDING
```

worker 异步：

- 删除旧 asset 视觉 chunk。
- 删除旧 Milvus vector。
- 按人工修正内容生成新 chunk。
- 写新向量。
- asset 回到 `READY`。

### 10. Asset 级 citation

RAG citation 命中视觉 chunk 时，返回 asset 引用：

```json
{
  "contentType": "IMAGE_CAPTION",
  "asset": {
    "assetId": "...",
    "assetType": "IMAGE",
    "title": "退款流程图",
    "section": "退款流程",
    "thumbnailUrl": "...",
    "originalUrl": "..."
  }
}
```

资产访问复用文档所属 space 的 `VIEWER` 权限。后端校验后返回短期 MinIO presigned URL 或重定向。

## 状态设计

### 文档状态

新增：

```text
READY_WITH_PENDING_ASSETS
READY_WITH_ASSET_ERRORS
```

状态计算：

```text
正文摄取失败
  -> FAILED

正文完成，存在 PENDING / PROCESSING / REINDEX_PENDING / REINDEXING
  -> READY_WITH_PENDING_ASSETS

正文完成，所有 asset READY
  -> READY

正文完成，存在 FAILED 且无 pending
  -> READY_WITH_ASSET_ERRORS

正文完成，同时存在 FAILED 和 pending
  -> READY_WITH_PENDING_ASSETS
```

### 资产状态

```text
PENDING
PROCESSING
READY
FAILED
REINDEX_PENDING
REINDEXING
```

## 失败处理

| 场景 | 处理 |
|------|------|
| 正文解析失败 | 文档 `FAILED` |
| 图片文件缺失 | asset `FAILED`，生成 `IMAGE_REFERENCE`，文档最终 `READY_WITH_ASSET_ERRORS` |
| Mermaid/PlantUML 渲染失败 | 保留源码说明，记录错误，不阻断正文 |
| OCR 失败但 caption 成功 | 生成部分视觉说明，记录 OCR 失败 |
| caption 失败但 OCR 成功 | 生成 OCR 基础说明，记录 caption 失败 |
| OCR/caption 都失败 | asset `FAILED` |
| 人工修正 reindex 失败 | asset 保持可重试状态并记录 `last_error` |
| MinIO 删除失败 | 记录 cleanup 问题，不静默吞掉 |

## 重新摄取

第一版采用全量清理重建：

```text
1. 删除旧 Milvus vectors
2. 删除旧 document_chunks
3. 查询旧 document_assets
4. 删除旧 MinIO objects
5. 删除旧 document_assets
6. 重新解析、上传资产、生成 chunks、写向量
```

预留 `content_hash` 用于后续增量复用。

## 分期实施

### Phase 1：资产基础设施

- zip 上传。
- 安全解压。
- 原始 zip 存 MinIO。
- `document_assets`。
- `document_chunks` 扩展。
- Markdown 图片抽取。
- Mermaid / PlantUML 抽取与渲染。
- `IMAGE_REFERENCE` / `DIAGRAM_SOURCE` chunk。

验收：

- 上传含图片和 Mermaid 的 zip。
- assets 表能看到图片和流程图。
- 检索图片 alt/path/section 和 Mermaid 节点能命中。

### Phase 2：异步视觉理解

- asset worker。
- OCR/caption provider 抽象。
- 外部模型调用配置。
- `IMAGE_CAPTION` / `DIAGRAM_SUMMARY` chunk。
- 文档状态计算。

验收：

- 图片处理完成后 RAG 能回答图片内容。
- 图片处理失败时正文仍可用，状态可见。

### Phase 3：引用与人工修正

- asset API。
- presigned URL。
- citation 扩展。
- manual caption/summary。
- async reindex。
- section + anchor chunk context expansion。

验收：

- 问答引用能返回 asset。
- 用户可打开图片。
- 人工修正后重新检索命中新内容。

## 非目标

- 不做 L3 多模态图片向量检索。
- 不做图片区域级引用。
- 第一版不支持远程 URL 图片。
- 第一版不支持 base64 图片。
- 第一版不支持 zip 内多个主 Markdown。
- 第一版不引入 MQ。

## 影响

### 正面影响

- Markdown 图片和流程图不再丢失语义。
- RAG 可回答图片/流程图内容。
- 用户可追溯答案引用到具体图片或流程图。
- 后续可平滑升级到 L3 多模态检索。
- OCR/caption 可失败重试和人工修正。

### 成本和复杂度

- 新增 `document_assets`、worker、MinIO 对象生命周期、citation 扩展。
- 需要处理 document/chunk/vector/asset/object 的一致性。
- 外部视觉模型会带来延迟、成本和失败重试问题。
- 检索后需要做图文 context expansion 和 asset citation 合并。

## 备选方案

### 方案 A：轻量 L2

只把 Mermaid/PlantUML 源码和 Markdown 图片 alt/path 转成文本 chunk。

放弃原因：

- 无法理解图片本体。
- 无法引用原图。
- 不满足完整图文资产管理诉求。

### 方案 B：L3 多模态检索

图片进入多模态 embedding 或图像向量库。

暂不采用原因：

- 引入多模态模型、图片向量、检索融合、region citation，复杂度过高。
- 当前项目已有文本 RAG 基础设施，先用 L2 可获得大部分收益。

### 方案 C：图片只存 MinIO，不入向量库

图片只作为附件展示，不参与检索。

放弃原因：

- 无法回答图片内容。
- 不解决 Markdown 图文 RAG 的核心问题。

## 关联

- 功能方案：[[features/markdown-visual-rag-l2]]
- 文档摄取：[[features/document-ingestion]]
- 数据流：[[architecture/data-flow]]
- Milvus 决策：[[decisions/adr-002-milvus-vector-store]]
- 混合检索：[[decisions/adr-003-hybrid-search-rrf]]
