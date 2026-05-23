# 发现与决策：Markdown 可视化 RAG L2

## 需求
- 支持包含普通文本、图片、Mermaid 流程图、PlantUML 流程图的 Markdown 文档。
- 图片二进制和渲染后的流程图存储到 MinIO。
- 通过新增 `document_assets` 表管理图片和流程图元数据。
- OCR/caption 可调用外部 provider，但视觉处理必须异步执行。
- 在视觉处理完成前，文本 RAG 仍然可用。
- 增加文档状态，用于表达资产处理中和资产处理失败。
- 将图片/流程图的语义文本作为 chunk 写入现有 `document_chunks` 表和现有 Milvus collection。
- RAG 答案支持资产级 citation。
- 支持人工修正生成的 caption/summary，并异步重建索引。
- 支持 Markdown zip 压缩包上传，第一版只处理一个主 Markdown 文件。

## 调研发现
- 当前应用配置和文档服务已支持 Markdown MIME 类型上传。
- 当前解析器会将非 PDF/非 HTML 文件交给 Tika 处理，因此 Markdown 中的图片和流程图没有一等解析能力。
- 当前入库流程是 parse -> chunk -> vector store -> chunk metadata -> document READY。
- 当前搜索流程默认基于 `document_chunks` 中的文本 chunk 和 `kb_chunks` Milvus collection。
- 当前 `DocumentChunk` 模型存储文本内容、Milvus ID、页码、chunk index，但没有资产和内容类型字段。
- 当前项目整体技术栈已经使用 MinIO 做对象存储，因此图片适合存 MinIO，而不是 PostgreSQL。
- 当前项目没有视觉资产表、OCR/caption worker，也没有资产级 citation 结构。

## 技术决策
| 决策 | 原因 |
|------|------|
| 采用完整 C 方案：L2 可视化 RAG | 用户希望做生产级图片/流程图处理，而不是轻量文本抽取。 |
| 二进制资产存 MinIO | 避免二进制进入 PostgreSQL，并支持安全访问。 |
| `document_assets` 作为事实来源 | 用于生命周期、状态、OCR/caption、人工修正和重新处理。 |
| 视觉文本复用现有 `document_chunks` + Milvus | 复用当前 RAG 搜索路径，并保持 L2 是文本投影。 |
| 视觉理解异步执行 | 外部 OCR/caption 可能慢且不稳定，文本应先变为可查询。 |
| 使用数据库轮询 worker | 第一版无需引入 MQ，也能满足重启恢复和重试控制。 |
| zip 作为 Markdown 归档上传 | 保留图片相对路径和目录结构。 |
| 第一版只支持安全相对图片路径 | 避免路径穿越、任意文件读取、SSRF 和 base64 payload 风险。 |
| Mermaid/PlantUML 渲染到 MinIO | 支持资产引用和可视化展示，同时保留源码降级检索。 |
| 使用 section + anchor chunk index 关联图文 | 在实用关联能力和避免第一版引入关系图复杂度之间取得平衡。 |
| 资产级 citation | 在不引入 L3 区域级复杂度的前提下，给用户可见、可打开的图片引用。 |
| 人工修正后异步重建索引 | 人工修正应更新检索结果，但不应让保存接口阻塞在 embedding/Milvus 调用上。 |
| 重新入库时全量清理旧数据 | 第一版避免旧资产、旧 chunk、旧 vector、旧对象残留。 |

## 阶段拆分

### Phase 1：资产基础设施
- 建立 schema、解析和存储基础。
- 暂不依赖外部 OCR/caption。
- 产出可搜索的 `IMAGE_REFERENCE` 和 `DIAGRAM_SOURCE` chunk。

### Phase 2：异步视觉理解
- 增加 worker 和 provider 抽象。
- 生成 `IMAGE_CAPTION` 和 `DIAGRAM_SUMMARY` chunk。
- 计算文档的视觉处理就绪/错误状态。

### Phase 3：引用与人工修正
- 增加资产 API、鉴权图片访问、资产 citation、人工修正与重建索引。
- 基于 section 和 anchor chunk index 增加图文上下文扩展。

### Phase 4：验证
- 增加入库、worker 行为、检索、引用、重新入库清理的测试样例和自动化测试。

### Phase 5：文档与上线
- 补充上传格式、运维限制、视觉 provider 行为和已知限制说明。

## 待确认问题
1. 第一版使用哪个 OCR provider？
2. 第一版使用哪个 caption/VLM provider？
3. zip 和图片的精确安全限制是多少？
4. 资产内容接口返回 JSON presigned URL，还是直接 302 跳转？
5. Mermaid/PlantUML 渲染在本地执行，还是通过独立服务执行？

## 相关资源
- 解析器实现：`kb-document/src/main/java/com/enterprise/kb/document/service/impl/DocumentParserServiceImpl.java`
- 入库流水线：`kb-document/src/main/java/com/enterprise/kb/document/pipeline/DocumentIngestionPipeline.java`
- chunk 元数据服务：`kb-document/src/main/java/com/enterprise/kb/document/service/impl/ChunkMetadataServiceImpl.java`
- chunk 模型：`kb-document/src/main/java/com/enterprise/kb/document/model/DocumentChunk.java`
- 语义检索：`kb-search/src/main/java/com/enterprise/kb/search/service/impl/SemanticSearchServiceImpl.java`
- 关键词检索：`kb-search/src/main/java/com/enterprise/kb/search/service/impl/KeywordSearchServiceImpl.java`
- Wiki 设计文档：`wiki/features/markdown-visual-rag-l2.md`
- 详细设计笔记：`wiki/features/markdown-visual-rag-l2-design-notes.md`
- ADR：`wiki/decisions/adr-011-markdown-visual-rag-l2.md`
- Wiki 导航：`wiki/Home.md`
- 文档入库 Wiki：`wiki/features/document-ingestion.md`

## 视觉/浏览器观察
- 本计划未使用图片观察或浏览器观察。

## 遇到的问题
| 问题 | 处理结果 |
|------|----------|
| 第一次实现尝试发生在设计完全确认前 | 停止实现，将沟通中的决策整理为本计划。 |
