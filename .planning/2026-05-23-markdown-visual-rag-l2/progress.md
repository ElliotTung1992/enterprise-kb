# 进度日志：Markdown 可视化 RAG L2

## 会话：2026-05-23

### Phase 0：方案澄清
- **状态：** complete
- **开始时间：** 2026-05-23
- 已完成动作：
  - 使用 `grill-me` 梳理 Markdown 图片和流程图进入 RAG 的设计决策树。
  - 对比 L2 和 L3 方案。
  - 选择完整 L2 可视化 RAG：资产存 MinIO，视觉语义以文本投影进入现有 RAG 链路。
  - 决定 OCR/caption 异步处理，并使用数据库轮询。
  - 决定按基础设施、视觉理解、引用/人工修正、验证、文档分阶段推进。
  - 按用户要求将 `.planning` 下本次新增计划文件改为中文。
- 创建/修改文件：
  - `.planning/2026-05-23-markdown-visual-rag-l2/task_plan.md`
  - `.planning/2026-05-23-markdown-visual-rag-l2/findings.md`
  - `.planning/2026-05-23-markdown-visual-rag-l2/progress.md`
  - `wiki/features/markdown-visual-rag-l2.md`
  - `wiki/features/markdown-visual-rag-l2-design-notes.md`
  - `wiki/decisions/adr-011-markdown-visual-rag-l2.md`
  - `wiki/Home.md`
  - `wiki/features/document-ingestion.md`

### Phase 1：资产基础设施
- **状态：** complete
- 已完成动作：
  - 新增 `document_assets` 数据表迁移和 Mapper/Model。
  - 为 `document_chunks` 增加 `content_type`、`asset_id`、`section`、`anchor_chunk_index` 字段。
  - 新增 `AssetType`、`AssetStatus`、`ChunkContentType`、`DiagramType` 常量。
  - 增加 Markdown zip MIME 支持和 zip 扩展名兜底识别。
  - 实现 zip 安全解压：路径穿越、绝对路径、文件数量、解压总大小限制。
  - 实现 Markdown 图片抽取，只处理安全相对路径，并上传图片原文件到 MinIO。
  - 实现 Mermaid/PlantUML fenced code 抽取，默认无渲染器时降级生成 `DIAGRAM_SOURCE`。
  - 将 `IMAGE_REFERENCE` / `DIAGRAM_SOURCE` 视觉 chunk 合并进现有向量写入和 chunk 元数据保存流程。
  - 删除文档和空间时同步删除视觉资产记录。
- 创建/修改文件：
  - `kb-app/src/main/resources/db/changelog/026-add-document-visual-assets.sql`
  - `kb-app/src/main/resources/db/changelog/db.changelog-master.xml`
  - `kb-app/src/main/resources/application.yml`
  - `pom.xml`
  - `kb-document/pom.xml`
  - `kb-common/src/main/java/com/enterprise/kb/common/constants/AssetStatus.java`
  - `kb-common/src/main/java/com/enterprise/kb/common/constants/AssetType.java`
  - `kb-common/src/main/java/com/enterprise/kb/common/constants/ChunkContentType.java`
  - `kb-common/src/main/java/com/enterprise/kb/common/constants/DiagramType.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/model/DocumentAsset.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/model/DocumentChunk.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/mapper/DocumentAssetMapper.java`
  - `kb-document/src/main/resources/mapper/DocumentAssetMapper.xml`
  - `kb-document/src/main/resources/mapper/DocumentChunkMapper.xml`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/DocumentObjectStorageService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/DiagramRenderService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/MarkdownVisualIngestionService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/MarkdownVisualIngestionResult.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/MinioDocumentObjectStorageService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/NoopDiagramRenderService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/MarkdownVisualIngestionServiceImpl.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/pipeline/DocumentIngestionPipeline.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/DocumentServiceImpl.java`

### Phase 2：异步视觉理解
- **状态：** pending
- 已完成动作：
  -
- 创建/修改文件：
  -

### Phase 3：资产引用与人工修正
- **状态：** pending
- 已完成动作：
  -
- 创建/修改文件：
  -

### Phase 4：验证与评估
- **状态：** pending
- 已完成动作：
  -
- 创建/修改文件：
  -

### Phase 5：文档与上线
- **状态：** pending
- 已完成动作：
  -
- 创建/修改文件：
  -

## 验证结果
| 验证项 | 输入 | 预期 | 实际 | 状态 |
|--------|------|------|------|------|
| 计划文件创建 | 用户要求基于已确认设计生成计划文件 | 计划文件位于 `.planning/2026-05-23-markdown-visual-rag-l2/` | 已确认文件和 active plan 指针存在 | Pass |
| Wiki 设计文档创建 | 用户要求生成对应 wiki 文件 | Wiki 页面和导航链接存在 | 已创建 `wiki/features/markdown-visual-rag-l2.md` 并完成链接 | Pass |
| ADR 创建 | 用户要求生成对应 decision 文件 | ADR 位于 `wiki/decisions` 并被链接 | 已创建 `adr-011-markdown-visual-rag-l2.md` 并完成链接 | Pass |
| 详细设计笔记创建 | 用户要求详细记录沟通过程和未来优化点 | 详细笔记文档存在，并从 feature/ADR 文档链接 | 已创建 `wiki/features/markdown-visual-rag-l2-design-notes.md` | Pass |
| `.planning` 文件中文化 | 用户要求 `.planning` 下新增文件改为中文 | 计划、发现、进度文件主体为中文 | 已将三个新增计划文件改为中文，技术标识保持原样 | Pass |
| Phase 1 编译验证 | 实现资产基础设施和 Markdown zip 视觉入库 | `kb-document` 及依赖模块可编译 | `mvn -pl kb-document -am -DskipTests compile` 通过 | Pass |

## 错误日志
| 时间 | 问题 | 尝试 | 处理结果 |
|------|------|------|----------|
| 2026-05-23 | 在用户要求先定方案前，曾开始向实现方向推进 | 1 | 停止实现，改为交付规划文档。 |

## 5 个问题快速恢复检查
| 问题 | 答案 |
|------|------|
| 我现在在哪里？ | 下一步进入 Phase 1：资产基础设施。 |
| 我要去哪里？ | 按阶段实现 Markdown 可视化 RAG L2。 |
| 目标是什么？ | 让 Markdown 文本、图片、流程图都能进入现有 RAG 系统，并可被检索和引用。 |
| 我已经知道了什么？ | 见 `findings.md`。 |
| 我已经做了什么？ | 已将确认后的 C 方案沉淀到计划文件，并按要求把 `.planning` 新增文件改为中文。 |
