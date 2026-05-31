# Markdown 图文 RAG L2 沟通纪要与优化清单（历史）

> 状态：设计沟通记录，**仅作历史保留**——所讨论的"在标准竖井内做图文 RAG"思路已被迁移 031 标准竖井整体退役所取代；当前活跃链路是 [[features/markdown-image-rag]]。
> 关联功能方案：[[features/markdown-visual-rag-l2]]
> 关联 ADR：[[decisions/adr-011-markdown-visual-rag-l2]]
> 关联计划：`.planning/2026-05-23-markdown-visual-rag-l2/`
> 接续：[[features/markdown-image-rag]]

## 背景问题

用户提出的问题是：当前 RAG 在处理 Markdown 文件时，如果文件中包含流程图和图片，应该如何处理。

经代码与现有设计确认，当前链路是文本型 RAG：

```text
Markdown 文件
  -> Tika 文本解析
  -> 文本分块
  -> 文本 embedding
  -> Milvus
  -> RAG 检索和问答
```

当前问题：

- Markdown 普通文字可以进入 RAG。
- Mermaid / PlantUML 等流程图如果是代码块，可能作为普通代码文本进入 RAG，但没有被识别为流程图，也没有图说明、渲染图或 asset 引用。
- Markdown 图片 `![alt](path)` 的图片本体不会被读取、OCR、caption 或入库。
- RAG citation 只能引用文本 chunk，无法引用“某张图片”或“某个流程图”。

## L2 与 L3 对比

讨论中先区分了两种方向。

### L2：图转文本后进入现有 RAG

核心：

```text
图片/流程图 -> OCR/caption/summary -> 文本 chunk -> 文本 embedding -> 现有 Milvus
```

优势：

- 复用现有文本 RAG 架构。
- 不需要图片向量库。
- 不需要多模态检索融合。
- 对流程图、截图说明、操作手册图片已经有明显收益。
- 工程复杂度低于 L3。

限制：

- 图片细节依赖 OCR/caption 质量。
- 不支持图片区域级定位。
- 不直接根据图片向量检索。

### L3：真正多模态检索

核心：

```text
文本 -> 文本向量
图片 -> 图像/多模态向量
查询 -> 图文联合检索
```

优势：

- 能更好回答图片视觉细节。
- 能处理 UI 截图、图表、设计图等视觉主导问题。
- 可进一步支持区域引用。

代价：

- 需要多模态 embedding / VLM。
- 需要图片 collection 或多模态 collection。
- 需要文本和图片检索结果融合。
- citation、权限、评估和前端都更复杂。

最终决策：当前做 **完整 L2**，不做 L3。

## 从轻量 L2 到完整 C 方案

最初讨论过三档方案：

```text
L1 / 轻量 L2：
  只保留 Mermaid/PlantUML 源码、图片 alt/path/title。

标准 L2：
  在轻量基础上，对图片做 OCR/caption。

完整 C：
  图片/流程图资产化，存 MinIO，建 document_assets，支持异步处理、asset citation、人工修正和重建索引。
```

用户选择了 **C 完整 L2**。

这意味着目标不是简单“让图片文字能搜到”，而是构建一套可生产使用的图文资产处理链路。

## 核心心智模型

沟通中反复澄清了一个关键点：图片会被拆成两类数据。

```text
图片本体：
  MinIO

图片资产元数据：
  document_assets

图片语义文本：
  document_chunks

图片语义向量：
  Milvus
```

也就是说：

```text
MinIO = 原件
document_assets = 档案 / source of truth
document_chunks = 检索说明书
Milvus = 检索说明书的向量索引
```

这不是无意义重复，而是“原始资产 + 检索投影”。

同理，原始 Markdown 是图文混合文档，但进入向量库时会拆成多个语义 chunk：

```text
TEXT chunk
IMAGE_REFERENCE chunk
IMAGE_CAPTION chunk
DIAGRAM_SOURCE chunk
DIAGRAM_SUMMARY chunk
```

这些 chunk 通过：

```text
documentId
section
assetId
chunkIndex
anchorChunkIndex
```

保持和原始文档的关系。

## 主要决策记录

### 1. 图片存 MinIO

结论：图片原件、流程图渲染图、缩略图和原始 zip 都存 MinIO。

原因：

- PostgreSQL 不适合存图片 blob。
- MinIO 更适合二进制对象。
- 方便前端查看原图。
- 支持后续重新 OCR/caption。

### 2. 建 `document_assets`

结论：必须建表，不只把图片说明塞进 chunk。

原因：

- 需要保存 object key。
- 需要保存 OCR/caption 状态。
- 需要支持失败重试。
- 需要人工修正。
- 需要 asset citation。
- 后续 L3 可扩展 image embedding / region 信息。

### 3. OCR/caption 可调用外部模型

用户明确表示 OCR/caption 可以调外部模型。

结论：

- 业务层抽象 `VisualUnderstandingService`。
- 内部拆成 `OCRService` 和 `CaptionService`。
- OCR 和 caption 分别配置并发、超时和重试。

### 4. OCR/caption 异步处理

用户选择异步方案。

原因：

- 外部视觉模型慢或失败时不阻塞正文。
- 文档正文可以先进入 RAG。
- 图片处理完后再追加视觉 chunk。

### 5. 文档状态增加中间态

结论：

```text
READY_WITH_PENDING_ASSETS
READY_WITH_ASSET_ERRORS
```

原因：

- 正文可用不等于图片已处理完。
- 用户和管理员需要知道图片是否仍在处理或失败。

### 6. 使用数据库轮询 worker

在 `@Async`、DB 轮询、MQ 三种方案中，选择 DB 轮询。

原因：

- 当前不引入 MQ。
- `document_assets` 自身就是任务状态表。
- 服务重启后可以继续扫 `PENDING`。
- 方便 retry_count / next_retry_at / last_error。

### 7. 图片语义 chunk 进入同一表和同一 Milvus collection

结论：

```text
document_chunks
Milvus kb_chunks
```

仍然作为统一检索入口。

原因：

- L2 的输出是文本。
- 复用现有 Hybrid Search / Rerank / RAG。
- 避免第一版做多 collection 结果融合。

### 8. Markdown 图片路径只支持安全相对路径

结论：

- 允许同目录或子目录相对路径。
- 禁止绝对路径。
- 禁止 `../` 逃逸。
- 禁止远程 URL。
- 禁止 base64 data URL。

原因：

- 防止任意文件读取。
- 防止 SSRF。
- 防止超大 base64 内存问题。
- 第一版安全边界更清晰。

### 9. Mermaid / PlantUML 渲染成图

结论：流程图源码要渲染成图片并存 MinIO。

原因：

- C 方案应能显示真实流程图。
- 渲染图可以用于 VLM caption。
- 渲染失败时仍可保留源码 summary。

### 10. 图文关系用 section + anchorChunkIndex

用户最终选择：

```text
先实现 B：documentId + section + chunkIndex 邻近关联
预留 C：未来显式图关系
```

原因：

- 比单纯 section 更准。
- 不需要第一版建复杂关系图。
- 后续可以从 `anchorChunkIndex` 回填显式关系表。

### 11. 支持人工修正 caption/summary

结论：支持 `manual_caption` 和 `manual_summary`。

原因：

- OCR/caption 不可能百分百准确。
- 企业知识库需要人工校正。
- 修正后应异步重建对应 chunk 和 Milvus 向量。

### 12. Citation 做到 asset 级

结论：

- RAG 命中图片 chunk 时，citation 返回 assetId、assetType、title、section、thumbnailUrl/originalUrl。
- 不做 region 级引用。

原因：

- asset 级引用足够覆盖 L2。
- region 级引用属于 L3/视觉定位范畴。

### 13. 缺失图片不拖垮正文

结论：

- 图片文件缺失时，asset 标记 `FAILED`。
- 仍生成 `IMAGE_REFERENCE` chunk。
- 文档最终可能进入 `READY_WITH_ASSET_ERRORS`。

原因：

- Markdown 正文仍然有价值。
- 用户需要知道图片缺失。
- 不应静默忽略。

### 14. 支持 zip 上传

结论：

- 第一版支持 Markdown zip 包上传。
- zip 里只允许一个主 `.md`。
- zip 只是上传容器，documents 记录代表主 Markdown。
- 原始 zip 存 MinIO。
- 本地临时解压目录处理后删除。

原因：

- Markdown 图片依赖相对目录。
- zip 能保留目录结构。
- 对用户最清晰。

### 15. 重摄取全量清理重建

结论：

- 删除旧 vectors。
- 删除旧 chunks。
- 删除旧 assets。
- 删除旧 MinIO objects。
- 重新解析和入库。

原因：

- 第一版优先一致性。
- 避免旧图和旧向量残留。
- `content_hash` 预留给后续增量复用。

## 分期实施记录

### Phase 1：资产基础设施

目标：先不接 OCR/VLM，打通资产抽取、MinIO、资产表和基础 chunk。

范围：

- zip 上传。
- 安全解压。
- 原始 zip 存 MinIO。
- `document_assets`。
- `document_chunks` 扩展。
- Markdown 图片抽取。
- Mermaid/PlantUML 抽取与渲染。
- `IMAGE_REFERENCE` / `DIAGRAM_SOURCE` chunk。

验收：

- 上传包含图片和 Mermaid 的 zip。
- assets 表能看到图片和流程图。
- 检索图片 alt/path/section 能命中。
- 检索 Mermaid 节点或流程关系能命中。

### Phase 2：异步视觉理解

目标：外部模型生成 OCR/caption/summary，并进入 RAG。

范围：

- asset worker。
- OCR/caption provider。
- 并发、超时、重试配置。
- `IMAGE_CAPTION` / `DIAGRAM_SUMMARY` chunk。
- 文档状态计算。

验收：

- 图片处理完成后 RAG 能回答图片内容。
- 失败时文档正文仍可用。
- asset 和 document 状态正确。

### Phase 3：引用展示与人工修正

目标：用户能看到答案引用了哪张图，并能修正图片说明。

范围：

- asset citation。
- asset API。
- MinIO presigned URL。
- manual caption/summary。
- async reindex。
- 图文邻近扩展。

验收：

- 问答引用返回 asset。
- 可打开图片。
- 人工修正后检索命中新内容。

### Phase 4：验证与评估

范围：

- Markdown fixture。
- 解析测试。
- worker 测试。
- 检索测试。
- citation 测试。
- 重摄取清理测试。

### Phase 5：文档与上线

范围：

- 上传说明。
- 状态说明。
- API 文档。
- 运维说明。
- 限制说明。

## 未来优化点

### 1. 从 DB 轮询升级到 MQ

当前选择 DB 轮询是为了降低一期复杂度。

未来当资产处理量变大，可以迁移到 MQ：

- asset created event。
- visual processing queue。
- retry queue。
- dead-letter queue。

收益：

- 更好削峰。
- 更好横向扩展 worker。
- 更清晰重试/死信语义。

### 2. 增量重摄取

当前重摄取全量清理重建。

未来可用：

```text
content_hash
original_path
source_code_hash
```

判断图片或流程图是否变化。

未变化的资产可以复用：

- MinIO object。
- OCR/caption。
- image caption chunk。
- Milvus vector。

收益：

- 减少外部模型调用成本。
- 减少重建索引时间。

### 3. 显式图文关系表

当前用：

```text
section + anchorChunkIndex
```

未来可新增：

```text
document_chunk_relations
```

记录：

```text
asset EXPLAINS chunk
asset NEAR chunk
chunk REFERENCES asset
asset BELONGS_TO section
```

收益：

- 长章节、多图文档中关联更准。
- 检索后上下文扩展更可控。
- 可做文档结构图。

### 4. L3 多模态检索

当前是 L2：图片转文本。

未来如果用户大量询问图片细节，可以引入 L3：

- 图片 embedding。
- 多模态向量 collection。
- 图文联合召回。
- 多模态 rerank。
- 图片区域级引用。

升级触发指标：

- 图片相关问题占比高。
- L2 图片问答准确率不足。
- 文档信息大量只存在图片中。
- 用户需要问布局、颜色、位置、按钮等视觉细节。

### 5. Region 级 citation

当前 citation 到 asset。

未来可扩展到：

```text
assetId + boundingBox
```

适用于：

- UI 截图。
- 图表。
- 架构图节点。
- 表格截图。

需要 OCR/VLM 输出区域坐标。

### 6. 图片补传和资产修复

当前缺失图片只记录失败。

未来可支持：

```text
POST /documents/{documentId}/assets/{assetId}/upload
```

用户补传缺失图片后：

- 上传 MinIO。
- asset 进入 `PENDING`。
- worker 处理 OCR/caption。
- 生成视觉 chunk。

### 7. 远程图片白名单

当前禁止远程 URL。

未来可按白名单支持：

- 域名白名单。
- 最大下载大小。
- 下载超时。
- MIME 校验。
- 禁止内网 IP。
- 防 SSRF。

### 8. 更强的流程图源码解析

第一版 Mermaid/PlantUML 解析可以先做基础关系抽取。

未来可增强：

- Mermaid flowchart 节点/边完整 AST。
- sequenceDiagram 参与者和消息。
- stateDiagram 状态转移。
- classDiagram 类关系。
- PlantUML activity/sequence/class 分类型解析。

收益：

- 即使渲染/VLM 失败，也能生成高质量 `DIAGRAM_SOURCE`。

### 9. 专门的视觉评估集

未来应建立图文 RAG eval cases：

```text
问题：退款流程图中审核失败后发生什么？
期望命中：assetId=...
期望答案包含：通知用户 / 重新提交
```

指标：

- Visual Recall@K。
- Asset Citation Accuracy。
- OCR/caption groundedness。
- 图文上下文扩展命中率。
- 图片相关 QA 成功率。

### 10. Asset 处理观测与运维面板

未来可加：

- pending asset count。
- failed asset count。
- OCR latency。
- caption latency。
- provider error rate。
- retry count。
- reindex backlog。

帮助定位外部视觉模型异常。

### 11. OCR/caption 成本控制

未来可做：

- 图片 hash 去重。
- 相同图片复用 caption。
- 小图/装饰图跳过。
- 低价值图片只 OCR 不 caption。
- 大文档按图片优先级处理。

### 12. 前端图文引用体验

未来可做：

- citation hover 显示缩略图。
- 点击打开原图。
- 显示 OCR/caption。
- 显示“人工修正”入口。
- 显示 asset processing 状态。

### 13. 文档状态细化

当前新增：

```text
READY_WITH_PENDING_ASSETS
READY_WITH_ASSET_ERRORS
```

未来可在 API 返回：

```text
assetTotal
assetReadyCount
assetPendingCount
assetFailedCount
```

UI 可以更准确展示处理进度。

## 仍需在实施前确定的问题

1. 第一个 OCR provider 选型。
2. 第一个 caption/VLM provider 选型。
3. Mermaid/PlantUML 渲染采用本地 CLI、容器还是服务。
4. zip 最大大小。
5. zip 最大文件数。
6. 单文档最大图片数。
7. 单图片最大大小。
8. asset content API 返回 JSON presigned URL 还是 302 redirect。
9. 缩略图是否一期生成。
10. MinIO 删除失败是否进入 orphan cleanup 任务表。

## 总结

这次沟通最终收敛为：

```text
完整 C 方案，但分期实施。
第一阶段先搭资产基础设施。
第二阶段接异步视觉理解。
第三阶段补 citation 和人工修正。
```

核心原则：

- 不让图片处理阻塞正文 RAG。
- 不把图片 blob 放数据库。
- 不把 L2 做成 L3。
- `document_assets` 是视觉资产主数据。
- `document_chunks + Milvus` 是可重建检索投影。
- 所有高复杂能力都预留演进路径，但第一版先保证一致性和可验证。
