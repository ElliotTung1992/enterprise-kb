# 实现计划：Markdown 结构感知 RAG（small-to-big）

> 配套设计：`docs/design-md-structure-rag.md`（定稿，C1–C17）。本文件是**可分阶段、可在新会话续作**的实现计划，含「Allowed APIs」与每阶段验证 + 反模式守卫。

## Phase 0 — Allowed APIs（已核实，引用真实代码，勿臆造）

**数据层（MyBatis + Liquibase）**
- Model：`@Getter @Setter` + 中文 JavaDoc 字段 + `Instant` 时间 + `Map<String,Object> metadata`（来源 `Document.java`）。
- 枚举：`com.enterprise.kb.common.constants.DocumentStatus`（PENDING/PROCESSING/READY/FAILED），`default-enum-type-handler=EnumTypeHandler`。
- TypeHandler：`com.enterprise.kb.document.typehandler.JsonbTypeHandler`（Map↔jsonb）、`UUIDTypeHandler`，经 `type-handlers-package` 自动注册。
- Mapper XML：UUID 参数 `#{x,jdbcType=OTHER}`；JSONB 写 `#{metadata,typeHandler=...JsonbTypeHandler,jdbcType=OTHER}`、读 `<result ... typeHandler="...JsonbTypeHandler"/>`；`insertBatch` 用 `<foreach collection="list" item="..." separator=",">`；软删 `deleted_at IS NULL`。
- 迁移：`--liquibase formatted sql` + `--changeset kb:027-...`；UUID PK `DEFAULT gen_random_uuid()`；`TIMESTAMPTZ ... DEFAULT NOW()`；索引 `idx_table_col`；在 `db.changelog-master.xml` 末尾 `<include file="db/changelog/027-....sql" relativeToChangelogFile="false"/>`。
- mybatis `type-aliases-package` 已含 `com.enterprise.kb.document.model`；新 md model 若放该包则别名可用（建议放 kb-document model 包）。

**向量 / 存储**
- `VectorStore` 由 Spring AI 自动配置（`application.yml: spring.ai.vectorstore.milvus`，集合 `kb_chunks`，1536/COSINE/IVF_FLAT）。**第二集合需手建 `mdVectorStore` bean** —— ⚠️ **Phase 3 第一步必须核对 pinned spring-ai 版本的 `MilvusVectorStore` 构造/builder API**（仓库无现成手建示例，勿照搬 agent 给的 `MilvusVectorStoreConfig.builder()` 旧式写法，须看实际 jar）。
- `VectorStoreService`：`upsert(List<Document>)` 内部 batch=100 调 `vectorStore.add`；`deleteByDocumentId` 用 `FilterExpressionBuilder().eq("documentId", id).build()` → `vectorStore.delete(expr)`。
- 入库元数据：`new Document(id, text, metadata)`，metadata 放 `documentId/spaceId/...`。
- 原始 `.md` 文件存 **MinIO**（`MdDocumentServiceImpl` 写 `md_documents.object_key`），ingestion worker 解析前下载到本地临时文件。
- `@Async("ingestionExecutor")`（`AppConfig`：`Executors.newVirtualThreadPerTaskExecutor()`）+ `@Transactional`。

**检索 / 问答**
- 关键词检索用 **JdbcTemplate** + `similarity(?, col)` + `ILIKE '%'||?||'%'`，`JOIN documents` 拿 title、`WHERE space_id=?::uuid AND deleted_at IS NULL`（来源 `KeywordSearchServiceImpl`）。md 版 JOIN `md_documents`、查 `md_child_chunk.embed_text`。
- DTO 均为 record：`SearchHit(chunkId,documentId,documentTitle,excerpt,pageNumber,score,mimeType,contentType,assetId,section,anchorChunkIndex)`、`SearchRequest(query,topK,modelProvider,filters,semanticQuery)`、`SearchResponse(hits,totalHits,searchMode,durationMs)`、`Citation(...)`、`QnARequest(question,sessionId,modelProvider,modelName,topK)`、`QnAResponse(answer,sessionId,citations,modelUsed,tokensUsed)`。
- `RerankService.rerank(question, List<SearchHit>, topN)`（复用）。
- `QaChatSessionService.saveExchange(sessionId,spaceId,userId,question,answer)`（复用）。
- `CitationAssembler.fromHits / citationKey / betterHit`（复用，包私有 → md 服务放同包 `com.enterprise.kb.search.service.impl`）。
- 语义检索：`vectorStore.similaritySearch(SearchRequest.builder().query(q).topK(k).filterExpression(b.eq("spaceId",id).build()).build())`（来源 `SemanticSearchServiceImpl`）。
- Controller：`@RestController @RequestMapping("/api/v1/spaces/{spaceId}/md-...")`；`@PreAuthorize("hasPermission(#spaceId,'SPACE','VIEWER'|'EDITOR')")`；`ApiResponse.ok(data[,msg])`；`PageResponse.of(pageInfo)`；上传 `@RequestParam("file") MultipartFile`，返回 202。
- Agentic：`FunctionToolCallback.builder(name, fn).description(...).inputType(X.class).build()`；`KnowledgeSearchInput(query)`；`traceReactAgentFactory.builder(name, trace.context()).chatClient(..).systemPrompt(..).tools(..).compileConfig(CompileConfig.builder().recursionLimit(n).build()).build()`；`AgenticTokenBudgetService.compute(question,history)`→`Budget(historyTokensMax,retrievalSpace)`、`compressHistory(...)`、`AGENT_SYSTEM_PROMPT`。

**反模式守卫（全局）**
- 不改标准竖井任何类（`DocumentController/Service`、`QnA*`、`HybridSearch`、`Semantic/KeywordSearchServiceImpl`、`document_chunks`、`kb_chunks`）。
- 不臆造 `MilvusVectorStore` API —— 先看 jar。
- 不引入 `space.type`、不抽 `DocumentIngestionPipeline` 接口、不加分派器。
- child 切分 **overlap=0**；表格 child = 线性化 `embed_text`，返回靠 char 区间回取 parent 原文。

---

## 阶段（对应设计 §11）

### Phase 1 — 数据层
- migration `027-create-markdown-rag.sql`：`md_documents`（object_key、status、chunk_count、metadata jsonb、软删）、`md_parent_chunk`、`md_child_chunk`（+ `gin (embed_text gin_trgm_ops)`）；注册进 master changelog。
- Model：`MdDocument`、`MdParentChunk`、`MdChildChunk`（放 `kb-document .../document/model`，复用 JsonbTypeHandler/DocumentStatus）。
- Mapper + XML：`MdDocumentMapper`（insert/update/findById/findBySpaceId/findIds/softDelete/delete）、`MdParentChunkMapper`（insertBatch/findById/findByIds/deleteByDocumentId）、`MdChildChunkMapper`（insertBatch/deleteByDocumentId + JdbcTemplate 关键词查询另置检索层）。
- **验证**：`mvn -q -pl kb-document -am install -DskipTests` 通过；migration SQL 用 `psql` 干跑或 liquibase validate。
- **守卫**：UUID `jdbcType=OTHER`；JSONB typeHandler；不碰 `document_chunks`。

### Phase 2 — 入库
- `MarkdownStructureIngestionService`（kb-document）：读 worker 从 MinIO 下载得到的本地临时 Markdown 文件 → parent 切分（H1-H3）→ child 切分（原子块贪心打包 + 超限句切 overlap=0 + 孤儿优先后向 + 表格线性化）→ 产出 `MdParentChunk`/`MdChildChunk` + Spring AI `Document`（embed_input=面包屑+embed_text）。
- `MdDocumentService` + 复制最小 `saveFile/validateFile/detectMimeType`（标准类是 private，不可复用 → 自带）。`@Async("ingestionExecutor")` ingest。
- **验证**：单测切分算法（A=500/B=1000、A=10/B=1000、表格线性化、超大表格行组）；编译通过。
- **守卫**：overlap=0；child.embed_text 不含面包屑。

### Phase 3 — 向量 + 关键词检索（md）
- **先核 `MilvusVectorStore` API**，再写 `mdVectorStore` bean（集合 `md_kb_chunks`，1536/COSINE，复用 `@Qualifier("dashscopeEmbeddingModel")`）。
- `MdVectorStoreService`（upsert/deleteByDocumentId，注入 `@Qualifier("mdVectorStore")`）。
- `MdVectorSearchService`（similaritySearch + spaceId filter → SearchHit，payload 带 parentId/seqInParent）。
- `MdKeywordSearchService`（JdbcTemplate，查 `md_child_chunk JOIN md_documents`）。
- **验证**：起应用，上传一个 .md，确认 `md_kb_chunks` 有向量、`md_child_chunk` 有行、关键词/语义各自能召回。
- **守卫**：勿臆造 Milvus API；勿污染 `kb_chunks`。

### Phase 4 — 融合 + parent 展开
- `MdHybridSearchService`（RRF 融合 md 向量+关键词，parentId 去重）。
- `ParentExpansionService`（topK child → 去重 parent → §8 整段/多窗节选 + 表格补表头）。
- `MdSearchHit`/`MdRetrievedPassage`（带 parentId、seqInParent）或扩展用法。
- **验证**：单测 RRF + 多窗节选（seq 0/9 命中、簇上限 3、硬顶）。

### Phase 5 — 标准问答
- `MdQnAController`（`/api/v1/spaces/{spaceId}/md-qa`：ask/advanced、ask/stream）+ `MdQnAService`（检索→rerank→ParentExpansion→prompt→ChatClient，复用会话/Citation）。
- `MdDocumentController`（上传/列表/删除 md_documents）。
- **验证**：端到端问答返回整段 parent；引用引 parent section。

### Phase 6 — Agentic（方案 D）
- `MdAgenticQnAService` + 两工具 `searchKnowledgeBase`（返 child 片段 + section 句柄）、`readFullSection(parentId)`（返完整 parent，§8 上限，去重已展开）；prompt 引导「信息不全则展开」。复用 `ReactAgent`/`AgenticTokenBudgetService`。
- `MdQnAController` 加 agentic 端点。
- **验证**：多跳问答能先搜后展开；预算不溢。

### Phase 7 — 清理监听
- md `SpaceDeletedEvent` 监听器：清 md 三表 + `md_kb_chunks`（标准 `handleSpaceDeleted` 不动）。`MdDocumentService.delete` 清 doc + 级联 + 向量。
- **验证**：删 md 文档 / 删空间后 md 三表 + 集合无残留。

### Phase 8 — 联调 + 最终验证
- 全量 `mvn install -DskipTests`；起应用；md 上传→入库→标准问答→agentic 问答→删除全链路。
- grep 守卫：确认标准竖井类无改动（git diff 仅新增 + 配置）；确认无 `overlap` 残留进 md child；确认 `md_kb_chunks` 与 `kb_chunks` 隔离。
</content>
