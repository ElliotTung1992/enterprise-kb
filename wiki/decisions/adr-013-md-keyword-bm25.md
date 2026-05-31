---
created: 2026-05-27
tags: [adr, architecture, rag, search, bm25, keyword-search, markdown, pg_tokenizer, vchord-bm25]
---

# ADR-013：md 关键词检索升级 BM25（VectorChord-bm25 + pg_tokenizer）

**状态**：已接受（代码已实现并编译验证；运行态建 model / 回填 / 评估 / 翻默认待执行，build 脚本截至 2026-05-27 尚未跑）

> 详细功能设计见 [[features/md-keyword-bm25]]。完整选型推演见 `docs/design-md-keyword-bm25.md`，实施计划见 `.planning/2026-05-28-markdown-keyword-bm25/plan.md`。本 ADR 是 [[decisions/adr-012-markdown-structure-rag]] 的后续——只升级该竖井的关键词检索一路。

## 背景

[[features/markdown-structure-rag]] 竖井的关键词路 `MdKeywordSearchServiceImpl` 用 `similarity(query, embed_text) + ILIKE '%query%'`，本质是 **pg_trgm 三元组相似度 + 子串匹配**，不是 BM25：无 IDF（高频词不降权）、无文档长度归一、无真正词级匹配；中文仅靠子串命中，长 query 易整串匹配不到。需要引入真正的 BM25 + jieba 中文分词。

> 前置：非 md 场景即将下线，故标准竖井（`document_chunks` / `KeywordSearchServiceImpl`）的同类改造**不做**。

## 决策

在 **Postgres 原生**实现 BM25，选 **VectorChord-bm25 + pg_tokenizer**，仅替换 md 竖井关键词路的内部算法，受 `keyword-mode` 开关控制（默认仍 TRGM）。

## 关键决策

### 1. BM25 跑在 Postgres 原生（船小好调头）

否决：应用层自算（df 维护 + 打分全自写易错）、内嵌 Lucene（第二个数据真相源）、升级 Milvus 2.5 原生 BM25（动到标准竖井也在用的共享集群，爆炸半径最大）。Postgres 原生不引入新引擎、不升级共享组件。

### 2. 选 VectorChord-bm25 + pg_tokenizer，而非 ParadeDB（C2）

决定性因素：**jieba 词级中文分词** + **自定义术语能力**（`create_synonym` / `create_stopwords` / `pg_dict` / 自训练 custom model 词表）。ParadeDB 的分词内置固定、同义词/停用词自定义基本没有。代价是换镜像 `tensorchord/vchord-suite:pg16`。

### 3. jieba 必须配自训练 custom model（带两个必接受的代价）

pre_tokenizer 用 jieba（`[pre_tokenizer.jieba]`），但 jieba **无法用预训练模型**，必须 `create_custom_model` 从 `md_child_chunk.embed_text` 学词表。由此：

- **空表冷启动**：全新部署 `md_child_chunk` 为空，学不出有效词表 → model 创建必须**等语料 ingest 之后**，不能塞进对空表运行的 migration。
- **加术语 = 重建**：词表是建模时冻结快照，改同义词/词表须 flag=TRGM 保护下重跑回填 + `REINDEX`。

> [!contradiction] 初版「jieba = IK 等价」理由不成立
> 初版选型写「jieba 可挂 user_dict，是 Elasticsearch IK 等价能力」。落地前读官方 `pg_tokenizer.rs` 核实：**jieba 无 user_dict 入口**。自定义术语改走 custom model 语料词表 + `create_synonym` 等。C2 结论不变，但**无任何 Postgres BM25 方案能等价 IK 的 user_dict 文件**——这是必须知道的边界。

### 4. 改动收敛到一格：RRF 按排名融合，无需归一化（最重要）

只动 `MdKeywordSearchServiceImpl.search()`，trgm/bm25 双分支返回**同形 `SearchHit`**。`MdHybridSearchServiceImpl`、RRF（k=60）、dense 路（留 Milvus）、`MdChildChunkMapper` **全不动**。根本原因：RRF 按**排名**融合、对分数绝对值/符号无感，所以 BM25（分为负）与 cosine 量纲不同也无需归一化。

### 5. migration 只做对空表安全的部分，建模挪到运维脚本

- **migration 029**：装扩展 + 加 `bm25vector` 列 + 建 `USING bm25` 索引——schema 级、对空表安全、纯 SQL 可复现。带**前置守卫**（仅 vchord-suite 镜像可装时执行，否则 `onFail:CONTINUE` 跳过不标记，应用照常走 TRGM）→ 新扩展不是启动硬依赖。
- **`db/manual/md-bm25-build.sql`**：jieba analyzer/model/tokenizer/trigger + 回填，依赖语料、**非 Liquibase migration**、语料后手动跑。

### 6. 入库方式 1：trigger 自动填充，mapper 不动

`create_custom_model_tokenizer_and_trigger` 建的 trigger 在 `INSERT/UPDATE embed_text` 时自动切词写 `bm25vector` ⇒ `insertBatch` 零改动，且回避冷启动期 tokenizer 不存在导致 insert 报错。存量靠一条 `UPDATE` 回填。

### 7. 开关 + 评估门控翻默认

`enterprise.kb.md.keyword-mode=TRGM|BM25`，默认 TRGM。手造 frozen 评估集（`MdKeywordEvalTest`）跑 BM25 vs TRGM recall@k，达标才翻默认；线上异常一键回退，无需重建数据。

## 非目标

- 标准竖井（`document_chunks` / `KeywordSearchServiceImpl`）任何改造（非 md 场景下线）。
- dense 语义检索改造（`MdVectorSearch` 继续走 Milvus `md_kb_chunks`，不迁向量到 Postgres）。
- RRF 融合策略调整、section 加权（本期保持输入与融合不变，只换算法）。
- 种子同义词表（`create_synonym`）——作为评估暴露 OOV 术语后的跟进项，不阻塞默认翻转。

## 影响

**正面**：md 关键词命中获得真正的 IDF + 长度归一 + jieba 词级中文分词；改动面极小（一个 method）；默认 TRGM + 一键回退，上线风险低；标准竖井零回归。

**成本**：换 Postgres 镜像（vchord-suite，较新组件需回归）；建 model 依赖语料、有冷启动顺序约束；改词表需重 tokenize 整列 + REINDEX；无 IK user_dict，硬术语收敛靠 custom model + 同义词表。

## 备选方案

- **应用层自算 / 内嵌 Lucene / 升级 Milvus 2.5**：见关键决策 1，均否决。
- **ParadeDB pg_search**：分词内置固定、自定义术语能力弱，见关键决策 2。
- **维持 pg_trgm**：无 IDF/长度归一/词级匹配，长中文 query 召回差，正是本 ADR 要解决的问题。

## 关联

- 功能方案：[[features/md-keyword-bm25]]
- 所属竖井 ADR：[[decisions/adr-012-markdown-structure-rag]]
- 混合检索决策：[[decisions/adr-003-hybrid-search-rrf]]（RRF k=60 按排名融合，本 ADR 据此免归一化）
- 标准竖井混合检索：[[ai-rag/hybrid-search]]
- 迁移：[[database/migrations]]（029）
