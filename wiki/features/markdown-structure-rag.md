# Markdown 结构感知 RAG（small-to-big 父子索引）

> 状态：已实现（编译通过，入库单测 7/7 通过），未端到端联调、未上线
> 设计文档：`docs/design-md-structure-rag.md`（含 C1–C17 决策）
> 架构决策：[[decisions/adr-012-markdown-structure-rag]]
> 验收：见下文 [[#验收]]（原独立验收页已并入本页）；完整逐条表 `docs/acceptance-md-structure-rag.md`
> 兄弟功能：[[features/markdown-visual-rag-l2]]（图文/视觉 RAG，另一回事）
> 关键词升级：[[features/md-keyword-bm25]]（本竖井关键词路 trgm → BM25，`keyword-mode` 开关切换）

> [!note] 与 [[features/markdown-visual-rag-l2]] 的关系（历史背景）
> 两者都叫「Markdown RAG」，但**起源不同**：
> - **图文 RAG L2**（ADR-011）：基于当时仍在的**标准竖井**（`documents` / `kb_chunks`）设计，处理 zip 内图片/流程图的 OCR/caption/视觉理解。**标准竖井随迁移 031 整体退役后**，其图片处理理念被并入 md 竖井（见 [[features/markdown-image-rag]]）。
> - **结构感知 RAG（本页）**：与图文 L2 同期上线、走全新平行竖井（`md_documents` / `md_kb_chunks`），按 H1-H3 结构切分 + small-to-big 父子索引 + 表格双表示。**当期不做视觉 / OCR**，与 L2 链路解耦。

> 当前 md 竖井是**唯一活跃的 RAG 链路**。原标准竖井（`DocumentController` / `QnAService` / `HybridSearchService` / `document_chunks` / `kb_chunks`）已随迁移 031 整体删除。

## 目标

1. `.md` 按**文档内部结构**（H1-H3 标题）切分，取代标准链「Tika 整篇 → 512 滑窗」的结构丢失。
2. **small-to-big 父子索引**：小 child 做召回（精准），完整 parent（整段 section）返回给 LLM（上下文完整）。
3. 表格**双表示**：检索用逐行自然语言化文本，返回给 LLM 用原始 markdown 表格。

## 当前竖井结构

md 竖井是项目内唯一活跃的 RAG 链路，由独立 controller 入口承接，无需 `space.type` 数据标志或分派器。

```
md 竖井
  MdDocumentController → MdDocumentService → md_documents
        → MarkdownStructureIngestion（parent/child 切分 + 表格线性化）
        → md_parent_chunk / md_child_chunk / Milvus md_kb_chunks
  MdQnAController → MdQnAService
        → MdHybridSearch（RRF，融合不去重）→ Rerank → ParentExpansion（回查 parent）→ prompt
```

历史并存的标准竖井（`DocumentController` / `QnAService` / `HybridSearchService` / `document_chunks` / `kb_chunks`）随迁移 031 整体退役。

只读复用、不修改的底座：`EmbeddingModel`、`ModelProviderResolver` / `ChatClient`、`RerankService`、`RedisChatMemory`、`QaChatSession`（会话表）、空间 RBAC。

## 数据模型

三张新表（迁移 027/028，见 [[database/migrations]]）+ 一个新 Milvus 集合：

| 对象 | 角色 |
|------|------|
| `md_documents` | md 文档元数据，状态机 `PENDING→PROCESSING→READY/FAILED` |
| `md_parent_chunk` | H1-H3 的 section，**永不进检索**，只按 id 回查正文（含原始 markdown 表格）|
| `md_child_chunk` | 段落级子块，`embed_text` 进检索；`char_start/char_end` 回指 parent 原文；建 `pg_trgm` GIN 索引 |
| Milvus `md_kb_chunks` | child 向量（1536 / COSINE / IVF_FLAT），由 `AppConfig.mdVectorStore` 手工装配 |

> `AppConfig` 手工建 `mdVectorStore` Bean；Spring AI Milvus 自动配置被 `spring.autoconfigure.exclude` 关闭（标准竖井退役后无需自动建集合）。

## 入库切分（关键复杂逻辑）

两阶段（见 `docs/design-md-structure-rag.md` §5.2 流程图）：

1. **原子块切分**：逐行扫描识别块类型（段落 / 代码块 / 表格 / 列表 / H4-H6）。代码块原子永不切；表格线性化；段落/列表超 `max-tokens`(512) 在此阶段按句子（overlap=0）/列表项切。
2. **贪心打包 + 孤儿救援（C17）**：顺序累积块到 child，加下一块会超 max 时——
   - `current ≥ min`(64) → flush 独立成 child（不跨块借句）；
   - `current < min`（孤儿）有前序兄弟 → **后向并入前一兄弟**；仅 section 首块、无前序兄弟 → **前向并入下一块**。
   - 尾部孤儿（最后一个 child < min 且有兄弟）→ 后向并入前一兄弟。

> 定心丸：child 边界只影响「哪个向量被命中」，回答阶段返回的是**完整 parent**，语义不被腰斩。因此用了 small-to-big 就**不再需要标准链的 100-token overlap**。

## 表格双表示

| 用途 | 内容 | 落点 |
|------|------|------|
| 检索（embed + pg_trgm）| 逐行自然语言化 `列名: 值；列名: 值` | `md_child_chunk.embed_text` |
| 返回 LLM | 原始 markdown 表格 | 按 child `char` 区间回取 parent 原文 |

大表格（>max）线性化行按 token 打包成多 child；返回某行组时**补回表头 + 分隔线**。

## 检索与问答

`MdQnAService`：query 改写 + HyDE（复用）→ `MdHybridSearch`（语义 + 关键词 RRF，**融合阶段不去重**，保留同 parent 多命中 child）→ `RerankService`（child 粒度精排）→ `ParentExpansion`（按 parentId 折叠去重，名次取最高分 child，回查 parent 正文 + **多窗节选**）→ prompt → 答案 + citations → 会话持久化。

- **去重延后到展开阶段**：融合阶段保留同 parent 的多个命中 child（保位置 `seq_in_parent`），供 §8.2 多窗节选「同一 section 命中多处各开一窗」。
- **多窗节选**：parent 整段超 `max-chars-per-parent`(2000) 时，按命中 child 的 seq 邻近度分簇（≤ `max-windows-per-parent`=3），每簇围绕命中向两侧扩，簇间插 `……（中间略）……`。

> [!note] 关键词路现走 BM25
> 上面 `MdHybridSearch` 的「关键词」一路现已默认走真正的 BM25（VectorChord-bm25 + pg_tokenizer jieba，build 脚本 2026-06-04 跑通，`MD_KEYWORD_MODE` 默认 `BM25`），可经 `enterprise.kb.md.keyword-mode=TRGM` 一键回退 pg_trgm（`similarity + ILIKE`）。详见 [[features/md-keyword-bm25]] / [[decisions/adr-013-md-keyword-bm25]]。RRF 按排名融合，对分数符号无感，故切换无需改融合逻辑。

### md Agentic（两工具，方案 D）

`MdAgenticQnAService` 复用 `ReactAgent`/预算/会话，提供两工具：

1. `searchKnowledgeBase(query)` — 走 `MdHybridSearch` + rerank，返回 ≤N 条小 child 片段，每条标注 `parentId` + 面包屑。
2. `readFullSection(parentId)` — 回查 parent 正文，按 `max-chars-per-parent` 裁剪，**同 parentId 不重复展开**（accumulator 记录）。

prompt 引导：child 片段足够则直接答；某条相关但残缺则对其 section 调 `readFullSection` 再答。`MAX_TOOL_CALLS=6`，递归上限 `2N+1`。

## API

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/v1/spaces/{spaceId}/md-documents/upload` | EDITOR（返回 202）|
| GET | `/api/v1/spaces/{spaceId}/md-documents` | VIEWER |
| GET / DELETE | `/api/v1/spaces/{spaceId}/md-documents/{documentId}` | VIEWER / EDITOR |
| POST | `/api/v1/spaces/{spaceId}/md-qa/ask` | VIEWER（同步）|
| POST | `/api/v1/spaces/{spaceId}/md-qa/ask/agentic` | VIEWER（两工具）|
| POST | `/api/v1/spaces/{spaceId}/md-qa/ask/stream` | VIEWER（SSE）|

前端验收页：`http://127.0.0.1:8081/md-rag.html`。

## 配置项（默认值）

```yaml
enterprise.kb:
  milvus.md-collection: md_kb_chunks
  chunking.markdown: { max-tokens: 512, min-tokens: 64, split-heading-levels: 3 }
  search.md-parent-expansion: { max-parents: 5, max-chars-per-parent: 2000, max-windows-per-parent: 3, max-context-chars: 10000 }
```

## 清理

- 文档删除（`MdDocumentService.deleteDocument`）：清 `md_kb_chunks` 向量 + child + parent，`md_documents` 软删除。
- 空间删除：`@EventListener handleSpaceDeleted(SpaceDeletedEvent)` 清 md 三表 + Milvus 集合。

## 验收

验收摘要（验收当期标准竖井尚在）：43 个文件 / +3575 行；新增迁移 `027` / `028`；新增 Milvus 集合 `md_kb_chunks`（1536 / COSINE / IVF_FLAT）；新增 REST 端点 文档 4 + 问答 3。对标准竖井零代码改动（仅 `AppConfig` 把自动配置的 `vectorStore` 标 primary）——该比较项在标准竖井随迁移 031 退役后已不再适用。

完整「验收点 → 方法 → 预期」逐条表见 `docs/acceptance-md-structure-rag.md` §3，分六组：

- **A 数据层**：三表 / 级联 / `pg_trgm` 索引 / `md_kb_chunks` 集合。
- **B 入库**：上传返回 202、状态机、H1-H3 parent 切分、child 512/64 打包、表格双表示、重新入库幂等清理、`chunk_count` 回填。
- **C 检索问答**：同步 / 流式 / Agentic 三端点、RRF 不去重、parent 折叠去重（≤5）、多窗节选、表格行组补表头、空检索兜底不编造、prompt 注入防护、多轮会话落库。
- **D 删除清理**：文档删除清向量 + `SpaceDeletedEvent` 监听清理。
- **E 权限**：上传 / 删除 EDITOR，查询 / 问答 VIEWER。
- **F 配置项**：8 个默认值核对（`md-collection` / `chunking.markdown.*` / `md-parent-expansion.*`）。

**自动化测试**：`MarkdownStructureIngestionServiceImplTest` 入库切分 7/7 通过（H1-H3 切分 + 面包屑前置、表格线性化、行内竖线非表格、大表格按行切、代码块原子、超长列表按项切、贪心打包孤儿方向 C17）。运行（`-am` 须加 `failIfNoSpecifiedTests=false`，否则在 `kb-common` 处因无匹配用例中断）：

```bash
mvn test -pl kb-document -am \
  -Dtest=MarkdownStructureIngestionServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

**遗留**：检索 / parent 展开 / Agentic 暂无自动化测试，需起 Milvus/PG/Redis/MinIO 手动联调；端到端 & 多轮会话回归未跑。设计决策 C1–C17 见设计文档。

**本期不做**：非 `.md` 格式（标准竖井退役后整体不支持）；知识图谱打标 / 文档关系（随迁移 031 删除）；图片 / 流程图视觉理解 / OCR / 表格 LLM 摘要 child（交 [[features/markdown-image-rag]]）；历史 `.md`（曾走标准链）自动回填 md 竖井。

## 关联文件

- 设计：`docs/design-md-structure-rag.md` · 验收：`docs/acceptance-md-structure-rag.md`
- 入库核心：`kb-document/.../MarkdownStructureIngestionServiceImpl.java`、`MdDocumentIngestionWorkerImpl.java`
- 检索核心：`kb-search/.../MdQnAServiceImpl.java`、`MdHybridSearchServiceImpl.java`、`MdParentExpansionServiceImpl.java`、`MdAgenticQnAServiceImpl.java`
