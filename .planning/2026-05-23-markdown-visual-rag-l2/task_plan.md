# 任务计划：Markdown 可视化 RAG L2

## 目标
设计并实现一套可用于生产环境的 Markdown 可视化入库链路，使 Markdown 文档中的文本、图片、流程图能够被解析、索引、检索并在现有 RAG 系统中被引用。

## 当前阶段
Phase 6

## 阶段拆分

### Phase 1：资产基础设施
- [x] 增加 Markdown 压缩包上传能力。
- [x] 增加 zip 安全校验：禁止路径穿越、禁止绝对路径、限制文件数量和解压后总大小。
- [x] 将 zip 视为上传容器，只为其中的单个主 Markdown 文件创建 `documents` 记录。
- [x] 将原始 zip 存储到 MinIO，并在处理完成后删除临时解压目录。
- [x] 新增 `document_assets` 表以及对应 Mapper/Service 层。
- [x] 为 `document_chunks` 增加 `content_type`、`asset_id`、`section`、`anchor_chunk_index` 字段。
- [x] 只解析 Markdown 中安全的相对路径图片。
- [x] 将图片原文件上传到 MinIO。
- [x] 抽取 Mermaid/PlantUML 流程图，并在默认无渲染器时降级为源码资产和源码 chunk。
- [x] 生成 `IMAGE_REFERENCE` 和 `DIAGRAM_SOURCE` 类型的 chunk。
- [x] 将文本 chunk 与图片引用/流程图源码 chunk 写入现有 Milvus collection。
- **状态：** complete

### Phase 2：异步视觉理解
- [x] 增加文档状态 `READY_WITH_PENDING_ASSETS` 和 `READY_WITH_ASSET_ERRORS`。
- [x] 增加资产状态：`PENDING`、`PROCESSING`、`READY`、`FAILED`、`REINDEX_PENDING`、`REINDEXING`。
- [x] 实现基于数据库轮询的资产处理 worker，用于处理待处理和可重试资产。
- [x] 在 `VisualUnderstandingService` 后面抽象 OCR 与 caption provider。
- [ ] 分别配置 OCR 与 caption 的并发数、超时时间和重试策略。
- [x] 将 OCR、caption、summary、entities 结果保存到 `document_assets`。
- [x] 按固定模板生成 `IMAGE_CAPTION` 和 `DIAGRAM_SUMMARY` chunk。
- [x] 将视觉语义 chunk 追加到 `document_chunks` 和现有 Milvus collection。
- [x] 根据资产状态重新计算文档状态。
- **状态：** in_progress

### Phase 3：资产引用与人工修正
- [x] 扩展 `Citation`，支持资产级引用。
- [x] 增加资产列表、详情、内容接口。
- [ ] 增加缩略图接口。
- [x] 通过文档所属知识空间的 `VIEWER` 权限校验资产访问。
- [x] 后端鉴权通过后返回短期有效的 MinIO presigned URL。
- [x] 增加人工修正 caption/summary 的字段和 API。
- [x] 人工修正后将资产标记为 `REINDEX_PENDING`。
- [x] worker 删除旧的资产视觉 chunk/vector，并基于人工修正内容重建。
- [ ] 增加基于 `documentId + section + anchor_chunk_index` 的检索上下文扩展。
- [x] 按 `assetId` 合并引用，优先使用 caption/summary chunk，弱化 reference/source chunk。
- **状态：** in_progress

### Phase 4：验证与评估
- [x] 增加 Markdown 图片、缺失图片、Mermaid、PlantUML 的解析单元测试。
- [x] 增加资产 worker 测试：成功处理、视觉理解失败重试、重试耗尽、人工重建索引。
- [x] 增加检索测试，验证资产级字段在混合检索融合后仍可保留。
- [x] 增加引用测试，验证资产级引用字段可输出。
- [x] 增加重新入库测试，确认旧资产、chunk、vector、MinIO 对象会被清理并重建。
- [x] 创建包含文本、图片、缺失图片、Mermaid、PlantUML 的 Markdown 测试样例。
- **状态：** complete

### Phase 5：文档与上线
- [x] 补充 Markdown zip 上传行为说明。
- [x] 补充视觉资产状态和重试行为说明。
- [x] 补充能力边界：本方案是 L2 文本投影，不是 L3 多模态图片检索。
- [x] 补充 MinIO 清理、视觉模型失败、重建索引任务等运维说明。
- [x] 更新资产接口和扩展引用结构的 API 文档。
- **状态：** complete

### Phase 6：引用质量增强
- [x] 增加统一 citation 组装器，普通 RAG 和 Agentic RAG 共用资产级引用去重规则。
- [x] 同一 `assetId` 命中多条视觉 chunk 时，只输出一条引用。
- [x] 同一资产优先保留 `IMAGE_CAPTION` / `DIAGRAM_SUMMARY`，弱化 `IMAGE_REFERENCE` / `DIAGRAM_SOURCE`。
- [x] Agentic RAG 工具调用期间按 citation key 分配引用编号，避免多轮检索重复暴露同一视觉资产。
- [x] 增加 citation 去重和优先级单元测试。
- **状态：** complete

## 关键问题
1. 首个生产环境优先接入哪个外部 OCR provider 和 caption/VLM provider？
2. Mermaid/PlantUML 渲染应使用本地 CLI/容器化渲染器，还是独立的内部渲染服务？
3. 首个生产版本中 zip 大小、解压文件数、图片数量、图片大小分别限制多少？
4. 资产 URL 接口应返回包含 presigned URL 的 JSON，还是直接返回 302 跳转？

## 已确定决策
| 决策 | 原因 |
|------|------|
| 实现完整 L2 可视化 RAG，而不是 L3 多模态检索 | 复用现有文本 embedding + Milvus RAG 链路，同时让图片和流程图内容可检索。 |
| 图片二进制和渲染后的流程图存储到 MinIO | 避免二进制进入 PostgreSQL，并支持鉴权后的资产访问。 |
| 新增 `document_assets` | 用于承载资产生命周期、MinIO object key、OCR/caption 状态、人工修正和未来 L3 扩展。 |
| OCR/caption 异步执行 | 避免慢速或不稳定的外部视觉模型阻塞文档文本可用。 |
| 第一版使用数据库轮询 worker | 当前特性不强依赖 MQ；数据库轮询具备重启恢复和重试控制能力，基础设施成本更低。 |
| 增加 `READY_WITH_PENDING_ASSETS` 和 `READY_WITH_ASSET_ERRORS` | 让视觉处理的部分完成/失败状态可见，同时不阻塞文本 RAG 使用。 |
| 复用 `document_chunks` 表和 Milvus collection | L2 产物本质是文本投影，可复用现有混合检索和 rerank 流程。 |
| 第一版只支持安全相对路径图片 | 避免任意文件读取、SSRF、base64 内存膨胀和远程下载风险。 |
| Mermaid/PlantUML 渲染为 MinIO 资产 | 支持资产引用和后续可选 VLM caption，同时保留基于源码的降级检索。 |
| 使用 section + anchor chunk index 关联图文 | 比只依赖章节更精确，同时避免第一版引入显式关系图的复杂度。 |
| 支持人工修正 caption/summary | OCR/caption 可能不准确，企业知识库需要可修正、可重建索引。 |
| 人工修正后的重建索引异步执行 | 避免用户保存操作被 embedding/Milvus 调用拖慢，并复用 worker 重试能力。 |
| 使用资产级 citation | 让答案可以引用并打开原图片/渲染后的流程图，不引入 L3 区域级复杂度。 |
| 重新入库时清理旧资产/chunk/vector/MinIO 对象 | 第一版优先保证正确性和一致性，不做增量复用。 |
| 视觉 chunk 使用固定模板 | 稳定检索行为，并让生成内容更容易排查问题。 |
| zip 上传只为主 Markdown 创建文档 | zip 是上传容器，业务文档仍是 Markdown 文件。 |
| 原始 zip 存储到 MinIO，临时解压目录处理后删除 | 保留源文件可追溯性，同时避免本地磁盘持续增长。 |

## 数据模型草图

### `document_assets`
| 字段 | 用途 |
|------|------|
| `id` | 资产 UUID。 |
| `document_id` | 所属文档。 |
| `asset_type` | `IMAGE` 或 `DIAGRAM`。 |
| `asset_index` | 在 Markdown 源文中的出现顺序。 |
| `original_path` | Markdown 中引用的原始路径。 |
| `object_key` | 原图片或渲染后流程图在 MinIO 中的 key。 |
| `thumbnail_object_key` | 可选缩略图在 MinIO 中的 key。 |
| `mime_type` | 检测后的 MIME 类型。 |
| `file_size` | 资产字节大小。 |
| `section` | 所在 Markdown 标题上下文。 |
| `anchor_chunk_index` | 最近文本 chunk 的位置。 |
| `alt_text` | Markdown 图片 alt 文本。 |
| `title` | Markdown 图片 title 或推断标题。 |
| `source_code` | Mermaid/PlantUML 流程图源码。 |
| `ocr_text` | OCR 结果。 |
| `caption` | provider 生成的图片描述。 |
| `summary` | provider 生成的检索摘要。 |
| `manual_caption` | 人工覆盖描述。 |
| `manual_summary` | 人工覆盖摘要。 |
| `status` | 资产处理状态。 |
| `retry_count` | 重试次数。 |
| `next_retry_at` | worker 调度字段。 |
| `last_error` | 最近一次失败原因。 |
| `content_hash` | 未来增量复用支持字段。 |
| `metadata` | JSONB 扩展字段。 |

### `document_chunks` 新增字段
| 字段 | 用途 |
|------|------|
| `content_type` | `TEXT`、`IMAGE_REFERENCE`、`IMAGE_CAPTION`、`DIAGRAM_SOURCE`、`DIAGRAM_SUMMARY`。 |
| `asset_id` | 可为空，关联 `document_assets`。 |
| `section` | Markdown 所属章节标题。 |
| `anchor_chunk_index` | 视觉内容与附近文本的锚点。 |

## 视觉 Chunk 模板

### `IMAGE_REFERENCE`
```text
[图片引用]
图片标题：{title or alt or filename}
所在章节：{section}
原始路径：{originalPath}
替代文本：{altText}
处理状态：{status}
[/图片引用]
```

### `IMAGE_CAPTION`
```text
[图片说明]
图片标题：{title or alt or filename}
所在章节：{section}
原始路径：{originalPath}
替代文本：{altText}
OCR文字：{ocrText}
图片描述：{manualCaption or caption}
检索摘要：{manualSummary or summary}
关键实体：{entities}
[/图片说明]
```

### `DIAGRAM_SOURCE`
```text
[流程图源码说明]
图标题：{title or section}
图类型：{MERMAID / PLANTUML}
所在章节：{section}
流程关系：{parsedRelations}
源码摘要：{sourceSummary}
[/流程图源码说明]
```

### `DIAGRAM_SUMMARY`
```text
[流程图说明]
图标题：{title or section}
图类型：{MERMAID / PLANTUML}
所在章节：{section}
流程关系：{parsedRelations}
OCR文字：{ocrText}
图描述：{manualCaption or caption}
检索摘要：{manualSummary or summary}
[/流程图说明]
```

## 遇到的问题
| 问题 | 尝试 | 处理结果 |
|------|------|----------|
| 初始尝试在方案最终确定前进入代码实现 | 1 | 用户明确要求先做规划；未应用代码补丁，转为生成计划文档。 |

## 备注
- 本计划刻意拆分原始资产、资产元数据、检索 chunk 和向量索引记录。
- `document_assets` 是视觉语义的事实来源；`document_chunks` 和 Milvus 是可重建的检索投影。
- 完整 L3 多模态图片向量检索明确不在本计划范围内。
