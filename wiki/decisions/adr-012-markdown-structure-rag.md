---
created: 2026-05-26
tags: [adr, architecture, rag, document-ingestion, markdown, small-to-big, parent-child]
---

# ADR-012：Markdown 结构感知 RAG（small-to-big 父子索引）

**状态**：已接受（已实现，待端到端联调与上线）

> 详细功能设计见 [[features/markdown-structure-rag]]。验收报告见 [[features/markdown-structure-rag-acceptance]]。完整决策推演（C1–C17）见 `docs/design-md-structure-rag.md`。

## 背景

标准链对 `.md` 的处理是「Tika 整篇 → 512 token 滑窗（100 overlap）」，**丢失文档内部结构**：标题层级、段落边界、表格都被压平成滑窗文本。这带来两个问题：

- 检索命中的 chunk 往往跨越无关段落，召回精度低。
- 表格被切碎，行与列名错位，检索几乎不可用。

同期 [[features/markdown-visual-rag-l2]]（ADR-011）解决的是 Markdown **图文/视觉**问题（图片 OCR、流程图 caption），走标准竖井。本 ADR 解决的是 Markdown **正文结构**问题，是另一条独立诉求。

## 决策

新建一条**与标准 RAG 完全平行的 md 竖井**，对纯 `.md` 做结构感知的 small-to-big 父子索引：

```text
MdDocumentController → MdDocumentService → md_documents
      → MarkdownStructureIngestion（H1-H3 parent + 段落级 child + 表格线性化）
      → md_parent_chunk / md_child_chunk / Milvus md_kb_chunks
MdQnAController → MdQnAService
      → MdHybridSearch(RRF，融合不去重) → Rerank(child) → ParentExpansion(回查 parent + 多窗) → prompt
```

核心心智：**小 child 做召回（精准），完整 parent（整段 section）返回给 LLM（上下文完整）**。

## 关键决策

### 1. 路由在 API 入口层，放弃分派器（最重要）

前端 md 页面调 `MdDocumentController` / `MdQnAController`，老页面调原 controller。路由发生在**控制器层**，不需要 `space.type` 数据标志，也不需要分派器。

因此**放弃**早期方案的「抽 `DocumentIngestionPipeline` 接口 + 分派器 + 改 `DocumentServiceImpl`」——**标准竖井一行不动**。唯一触碰：`AppConfig` 新增 `mdVectorStore` Bean，并用 `vectorStorePrimaryPostProcessor` 把自动配置的 `vectorStore` 标 primary 以消除类型注入歧义。

### 2. small-to-big：child 召回、parent 返回（C1 / C2）

- **C1**：即使一个 section 只有一个 child（整段 ≤ max），仍建 parent 行。统一 `child → parent` 路径，检索零分支；存储冗余是 small-to-big 的固有取舍。
- **C2**：child 只存裸 `embed_text`（检索文本），**返回给 LLM 的内容不在 child 上存第二份**，用 `char_start/char_end` 回 `md_parent_chunk.content` 取原文。

### 3. 弃用标准链的 100-token overlap

overlap 与 parent 展开解决的是同一问题——边界上下文丢失。用了 small-to-big 就不再需要 overlap（二者并用属冗余）：① 命中即返回整段 parent，边界天然补全；② child 按语义边界（段落/句子/块）切，概念不被腰斩。收益：索引更小、不重复计分、去重更干净。

### 4. 表格双表示

| 用途 | 内容 | 落点 |
|---|---|---|
| 检索（embed + pg_trgm）| 逐行线性化 `列名: 值；列名: 值` | `md_child_chunk.embed_text` |
| 返回 LLM | 原始 markdown 表格 | 按 child char 区间回取 parent 原文 |

大表格按线性化行打包成多 child；返回行组时补回表头 + 分隔线。每行自带列名 → 孤儿行问题消失。

### 5. 孤儿合并方向（C17）

贪心打包时，child 不足 `min`(64) 且加下一块会超 `max`(512) 即「孤儿」：

- **有前序兄弟 → 后向并入前一兄弟**（不扰动后块，下一块照常起新 child）；
- **仅 section 首块、无前序兄弟 → 前向并入下一块**。

定心丸：child 边界只决定「哪个向量被命中」，回答阶段返回完整 parent，语义不被腰斩。

### 6. 检索顺序 + 融合阶段不去重（C6 / §7）

顺序：**多源检索 → RRF → 在 child 上 rerank → topK child → 展开 parent**。

**融合阶段不去重**：若按 parentId 去重到「每 parent 只剩最高分 child」，会丢掉同一 section 其余命中 child 的 `seq_in_parent`，而多窗节选正需要这些位置「同一 section 命中多处时各开一个窗」。因此把同 parent 的多个命中 child 留到 rerank 与展开；`ParentExpansion` 再按 parentId 折叠（用 rerank 首个出现的 child 定名次），收齐全部命中 seq 供多窗。

### 7. md Agentic = 方案 D 两工具（C16）

`searchKnowledgeBase(query)` 搜 child + `readFullSection(parentId)` 按需展开 parent，prompt 引导信息不全时展开。同 parentId 不重复展开（accumulator）。复用 `ReactAgent`/预算/会话。

### 8. 其余取舍

- **C3**：面包屑只存 parent，embed 时给 child 临时前置；child 的 `embed_text` 存不含面包屑的裸文本。
- **C13**：md 问答复用标准链 `QueryRewrite` / `Hyde`。
- **C14**：md 文档**不参与**知识图谱打标 / 文档关系（本期 out）。
- **C15**：历史 `.md`（已走标准链）不自动迁移，需要则重新经 md 页面上传。
- **C5 默认参数**：`max-tokens=512` / `min-tokens=64` / `max-parents=5` / `max-chars-per-parent=2000` / `max-windows-per-parent=3` / `max-context-chars=10000`。
- **独立 Milvus 集合 `md_kb_chunks`**：仅 md 竖井查，无跨 `kb_chunks` 可比性要求。

## 非目标

- 非 `.md` 格式（走标准竖井）。
- 图片/流程图视觉理解、OCR、LLM caption（交给 [[features/markdown-visual-rag-l2]]）。
- 表格的 LLM 摘要 child。
- md 参与知识图谱 / 文档关系。

## 影响

**正面**：Markdown 结构与表格不再被滑窗压平；召回精准（小 child）+ 上下文完整（整段 parent）；标准竖井零回归风险。

**成本**：新增三表 + 一个 Milvus 集合 + 一整套 controller/service；parent 远大于旧 300 字 excerpt，须核对模型上下文窗口与成本；两竖井共用 `RerankService`/`QaChatSession`/`ChatClient`，改动须双向回归。

## 备选方案

- **抽 `DocumentIngestionPipeline` 接口 + 分派器**：早期方案，因路由在 controller 层、两链从 controller 往下完全平行而放弃，避免改动标准竖井。
- **复用标准链仅调 chunk 策略**：无法表达 parent/child 双层与表格双表示，且会污染标准竖井。
- **child 保留 overlap**：与 parent 展开冗余，放弃（保留「1 句引导式 overlap」作后续安全阀）。

## 关联

- 功能方案：[[features/markdown-structure-rag]]
- 验收报告：[[features/markdown-structure-rag-acceptance]]
- 兄弟 ADR（图文 RAG）：[[decisions/adr-011-markdown-visual-rag-l2]]
- 文档摄取：[[features/document-ingestion]]
- Milvus 决策：[[decisions/adr-002-milvus-vector-store]]
- 混合检索：[[decisions/adr-003-hybrid-search-rrf]]
- 迁移：[[database/migrations]]（027 / 028）
