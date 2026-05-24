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
- **状态：** in_progress
- 已完成动作：
  - 增加文档状态 `READY_WITH_PENDING_ASSETS`、`READY_WITH_ASSET_ERRORS`。
  - 为 `document_assets` 增加 `entities` 字段，并补充视觉理解结果更新 SQL。
  - 新增 `VisualUnderstandingService` 和 `VisualUnderstandingResult`，默认 `NoopVisualUnderstandingService` 先打通链路。
  - 新增 `VisualAssetWorkerServiceImpl`，通过 `@Scheduled` 数据库轮询处理 `PENDING` 资产。
  - worker 支持 `PENDING -> PROCESSING -> READY/FAILED` 状态流转和失败重试。
  - worker 生成 `IMAGE_CAPTION` / `DIAGRAM_SUMMARY` chunk，并追加写入 Milvus 与 `document_chunks`。
  - worker 根据资产状态回写文档状态，并刷新文档 chunkCount。
  - 文档入库完成时，如存在视觉资产，状态改为 `READY_WITH_PENDING_ASSETS`。
- 创建/修改文件：
  - `kb-common/src/main/java/com/enterprise/kb/common/constants/DocumentStatus.java`
  - `kb-app/src/main/resources/db/changelog/026-add-document-visual-assets.sql`
  - `kb-app/src/main/resources/application.yml`
  - `kb-document/src/main/java/com/enterprise/kb/document/model/DocumentAsset.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/mapper/DocumentAssetMapper.java`
  - `kb-document/src/main/resources/mapper/DocumentAssetMapper.xml`
  - `kb-document/src/main/java/com/enterprise/kb/document/mapper/DocumentChunkMapper.java`
  - `kb-document/src/main/resources/mapper/DocumentChunkMapper.xml`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/VisualUnderstandingService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/VisualUnderstandingResult.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/VisualAssetWorkerService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/NoopVisualUnderstandingService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/VisualAssetWorkerServiceImpl.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/pipeline/DocumentIngestionPipeline.java`

### Phase 3：资产引用与人工修正
- **状态：** in_progress
- 已完成动作：
  - 新增视觉资产 DTO、Service 和 Controller API。
  - 增加资产列表、详情、内容 URL 接口，复用空间级 `VIEWER` 权限。
  - 增加 MinIO 短期 presigned URL 生成能力。
  - 增加人工修正接口，保存 `manualCaption` / `manualSummary` 并将资产标记为 `REINDEX_PENDING`。
  - worker 支持处理 `REINDEX_PENDING`：删除旧资产视觉 chunk/vector，再用人工修正内容重建。
  - 扩展 `SearchHit` 和 `Citation`，带出 `contentType`、`assetId`、`section`、`anchorChunkIndex`。
  - 语义检索和关键词检索均返回资产级引用字段。
- 创建/修改文件：
  - `kb-document/src/main/java/com/enterprise/kb/document/dto/DocumentAssetDto.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/dto/AssetUrlResponse.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/dto/AssetCorrectionRequest.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/DocumentAssetService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/DocumentAssetServiceImpl.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/controller/DocumentController.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/DocumentObjectStorageService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/MinioDocumentObjectStorageService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/VectorStoreService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/VectorStoreServiceImpl.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/mapper/DocumentAssetMapper.java`
  - `kb-document/src/main/resources/mapper/DocumentAssetMapper.xml`
  - `kb-document/src/main/java/com/enterprise/kb/document/mapper/DocumentChunkMapper.java`
  - `kb-document/src/main/resources/mapper/DocumentChunkMapper.xml`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/VisualAssetWorkerServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/dto/SearchHit.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/dto/Citation.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/SemanticSearchServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/KeywordSearchServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/RerankServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java`

### Phase 4：验证与评估
- **状态：** complete
- 已完成动作：
  - 增加 Markdown 可视化入库测试样例，覆盖正文、图片引用、缺失图片、Mermaid 和 PlantUML。
  - 增加 `MarkdownVisualIngestionServiceImplTest`，验证 zip 安全解析、图片上传、缺失图片跳过和流程图资产抽取。
  - 增加 `VisualAssetWorkerServiceImplTest`，验证资产处理成功、失败重试、重试耗尽和人工修正后的重建索引。
  - 补齐重新入库前清理逻辑，确保旧 vector、旧 chunk、旧视觉资产记录和旧资产对象先清理再重建。
  - 增加 `DocumentIngestionPipelineTest`，验证重新入库清理顺序。
  - 增加混合检索测试，验证 `contentType`、`assetId`、`section`、`anchorChunkIndex` 在融合结果中保留。
  - 增加 `CitationTest`，验证资产级引用字段可被 DTO 输出。
- 创建/修改文件：
  - `kb-document/src/test/resources/markdown-visual-rag-l2/README.md`
  - `kb-document/src/test/resources/markdown-visual-rag-l2/images/architecture.svg`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/DocumentObjectStorageService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/MinioDocumentObjectStorageService.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/service/impl/MarkdownVisualIngestionServiceImpl.java`
  - `kb-document/src/main/java/com/enterprise/kb/document/pipeline/DocumentIngestionPipeline.java`
  - `kb-document/src/test/java/com/enterprise/kb/document/pipeline/DocumentIngestionPipelineTest.java`
  - `kb-document/src/test/java/com/enterprise/kb/document/service/impl/MarkdownVisualIngestionServiceImplTest.java`
  - `kb-document/src/test/java/com/enterprise/kb/document/service/impl/VisualAssetWorkerServiceImplTest.java`
  - `kb-search/src/test/java/com/enterprise/kb/search/service/impl/HybridSearchServiceImplTest.java`
  - `kb-search/src/test/java/com/enterprise/kb/search/dto/CitationTest.java`

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
| Phase 2 编译验证 | 实现异步视觉理解 worker 基础链路 | 应用及依赖模块可编译 | `mvn -pl kb-app -am -DskipTests compile` 通过 | Pass |
| Phase 3 编译验证 | 实现资产 API、人工修正、资产级 citation 字段 | 应用及依赖模块可编译 | `mvn -pl kb-app -am -DskipTests compile` 通过 | Pass |
| Phase 4 文档解析、worker 和重新入库清理测试 | Markdown 可视化样例、资产 worker 成功/失败/重建场景、重新入库清理顺序 | `kb-document` 指定测试通过 | `mvn -pl kb-document -am -Dtest=MarkdownVisualIngestionServiceImplTest,VisualAssetWorkerServiceImplTest,DocumentIngestionPipelineTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过 | Pass |
| Phase 4 检索和引用测试 | 资产级字段经过混合检索和 Citation DTO | `kb-search` 指定测试通过 | `mvn -pl kb-search -am -Dtest=HybridSearchServiceImplTest,CitationTest -Dsurefire.failIfNoSpecifiedTests=false test` 通过 | Pass |

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
