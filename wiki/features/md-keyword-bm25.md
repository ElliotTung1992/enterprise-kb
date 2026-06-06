---
created: 2026-05-27
tags: [feature, rag, search, bm25, keyword-search, markdown, pg_tokenizer, vchord-bm25]
---

# md 关键词检索升级 BM25（VectorChord-bm25 + pg_tokenizer）

> 状态：**代码已实现 + BM25 build 已跑通**（2026-06-04）。`db/manual/md-bm25-build.sql` 已执行，`tokenizer_catalog` 三表与 `md_model` 词表生成，运行时已确认走 BM25（不再降级 TRGM）。剩余可选运行态步骤：填评估集跑 `MdKeywordEvalTest` 做 recall@k 对照。
> 设计文档：`docs/md-structure-rag.md` 第三部分「关键词检索升级 BM25 子设计」（含选型 C1/C2 推演；该子设计已并入合订本，无独立文件）
> 实施计划：`.planning/2026-05-28-markdown-keyword-bm25/plan.md`
> 架构决策：[[decisions/adr-013-md-keyword-bm25]]
> 所属竖井：[[features/markdown-structure-rag]]（本功能只升级该竖井的**关键词那一路**）

## 目标

把 md 竖井关键词检索从 **pg_trgm 三元组相似度 + 子串匹配** 换成**真正的 BM25**（词频 × IDF × 文档长度归一）+ **jieba 中文分词**，提升字面/术语命中精度。

现状 `MdKeywordSearchServiceImpl` 用 `similarity(query, embed_text) + ILIKE '%query%'`：本质是 trgm + 子串，**无 IDF（高频词不降权）、无长度归一、无词级匹配**，长中文 query 容易整串匹配不到。

## 改动面极小：只换一格

md 混合检索结构不变，只换关键词那一路的内部实现：

```
MdQnAController → MdQnAService
   → MdHybridSearch( MdVectorSearch(dense, Milvus md_kb_chunks)   ← 不动
                   + MdKeywordSearch(关键词)                       ← 本功能：trgm → BM25（开关切换）
                   , RRF k=60 融合 )                               ← 不动
   → Rerank → ParentExpansion → prompt                            ← 不动
```

唯一**查询**改动点：`MdKeywordSearchServiceImpl.search()`（trgm 分支 + bm25 分支，`keyword-mode` 开关切换）。

> **为什么改动能收敛到一格**：RRF 按**排名**融合（k=60），不看分数绝对值。所以 BM25（分为负）与 cosine 量纲/符号不同也**无需归一化**——上游 `MdHybridSearchServiceImpl`、`SearchHit` 契约、dense 路、mapper 全部零感知。

## 方案选型

- **BM25 跑哪**：Postgres 原生（不引入新引擎、不升级共享 Milvus 集群，爆炸半径最小）。否决应用层自算、内嵌 Lucene、升级 Milvus 2.5。
- **哪套 Postgres BM25**：**C2 = VectorChord-bm25 + pg_tokenizer**（TensorChord/Rust），而非 ParadeDB pg_search（tantivy）。决定性因素：jieba 词级中文分词 + 自定义术语能力（`create_synonym` / `create_stopwords` / `pg_dict` / 自训练 custom model 词表）。
- **镜像**：换 `tensorchord/vchord-suite:pg16-20260501`（含三扩展，PG16 数据目录兼容）。

> [!contradiction] 初版选型理由的更正（已在设计文档就地修正）
> 初版写「jieba 可挂自定义词典 user_dict = IK 等价能力」。落地前读官方 `pg_tokenizer.rs` 原文核实，**pg_tokenizer 的 jieba 并无 user_dict 入口**。自定义术语改由「自训练 custom model 语料词表 + `create_synonym`/`create_stopwords`/`pg_dict`」实现。结论 C2 不变，但**没有任何 Postgres BM25 方案能等价 Elasticsearch IK 的 user_dict 文件**——这是边界。

## 切词/打分三件套：md_ta → md_model → md_tok

这套对象在运维脚本 `db/manual/md-bm25-build.sql` 里建出，是 BM25 链路的核心：

| 对象 | 角色 | 存储位置 |
|------|------|---------|
| `md_ta` | text analyzer = **怎么切词**（`[pre_tokenizer.jieba]`，中文分词规则）| `tokenizer_catalog.text_analyzer` |
| `md_model` | **词表**（词 → 整数 ID）+ 语料统计，从 `md_child_chunk.embed_text` 学习 | `tokenizer_catalog.model`（`name` / `config` 列）|
| `md_tok` | tokenizer = `md_ta` 切词 + `md_model` 查表，产出 `bm25vector` | `tokenizer_catalog.tokenizer` |

完整链路：

```
embed_text "猫 吃 猫 鱼"
  → md_ta(jieba) 切词         → [猫, 吃, 猫, 鱼]
  → md_model 查词典 词→ID      → [7, 12, 7, 30]   (tokenize 返回 integer[]，保留重复)
  → ::bm25vector 聚合计数      → {7:2, 12:1, 30:1}  ← 存进 md_child_chunk.bm25vector
```

> **词表是建模时的冻结快照**：`md_model` 在脚本执行那一刻从当时语料学一次词表，**不随后续 ingest 自动进化**。建模后才出现的新术语对 model 是 OOV。语料/术语漂移后想让词表跟上，须 flag=TRGM 保护下重训 model + 回填 + REINDEX（见风险）。这是一个**周期性运维动作**，不是每次写入的动作。

## bm25vector 数据结构（本会话实测）

`bm25vector` 是**稀疏向量**，文本形态 `{词ID:值, ...}`：

```
{7:3, 12:1, 30:1}
 │ │
 │ └─ 值 = TF（该词在本篇文档的出现次数）
 └─── 维度 = 词的 ID（md_model 分配）
```

实测出的结构特性：
- 内部按**词ID 严格升序**存储（乱序/重复字面量直接报 `Indexes are not increasing`）；为 BM25 算分的归并扫描服务。
- **TF 是"每个维度上的值"，不是单独的维度**；维度是词ID。
- `integer[] → bm25vector` 的 cast 负责排序 + 同 ID 计数聚合（得出 TF），应用层/trigger 无需操心排序。
- 不带 pgvector sparsevec 的 `/N` 维数后缀（总维数是全局的，在 `md_model` 里）。

### TF / DF / IDF 各归各家

| 量 | 含义 | 谁维护 |
|----|------|--------|
| **TF**（本篇词频）| 词在**某篇**出现几次 | ✅ 每行 `bm25vector`（`id:值` 的值）|
| **DF / N / avgdl**（全库稀缺度）| 含某词的文档篇数、总文档数、平均长度 | ✅ **`USING bm25` 倒排索引** `idx_md_child_bm25`（DF = 倒排链长度）|
| **IDF**（稀缺度权重）| 由 DF、N 算出 | ❌ 不预存，查询时 `to_bm25query(index, query)` 现算 |

证据：`to_bm25query(index_oid regclass, query_vector bm25vector)` 构造查询时要传**索引** → IDF 权重从索引的全库统计取。打分是"两边凑"：**文档侧出 TF（从列读），查询侧带 IDF（从索引取）**，`search_bm25query` 合成最终分。

## 迁移与入库

**migration 029**（`029-add-md-bm25.sql`，只做对空表安全、纯 SQL 可复现的部分）：

```sql
CREATE EXTENSION IF NOT EXISTS pg_tokenizer CASCADE;
CREATE EXTENSION IF NOT EXISTS vchord_bm25 CASCADE;
ALTER TABLE md_child_chunk ADD COLUMN IF NOT EXISTS bm25vector bm25vector;       -- 可空
CREATE INDEX IF NOT EXISTS idx_md_child_bm25 ON md_child_chunk USING bm25 (bm25vector bm25_ops);
```

- 带**前置守卫**：仅当 `pg_tokenizer` + `vchord_bm25` 两扩展可装（跑在 vchord-suite 镜像）时才执行；否则 `onFail:CONTINUE` 跳过且不标记已执行，应用照常启动走 TRGM，日后换镜像重启会自动重评估。**新扩展不再是应用启动的硬依赖。**
- 喂 BM25 的文本是 `embed_text`（与现 trgm 路输入一致，保证 A/B 时只算法变、输入不变）。

**运维脚本 `db/manual/md-bm25-build.sql`**（**非 Liquibase migration**，不会自动执行；语料 ingest 后**手动**跑）：建 `md_ta` → `md_model` → `md_tok` + 自动填充 trigger，并一条 `UPDATE` 回填存量。

> **入库方式 1（trigger 自动填充，mapper 不动）**：trigger 在 `INSERT/UPDATE embed_text` 时自动切词写 `bm25vector` ⇒ `MdChildChunkMapper.insertBatch` **完全不改**，且回避冷启动期 tokenizer 不存在导致 insert 报错。存量行靠那条一次性 `UPDATE`（trigger 只对其创建后的写入生效）。脚本含手动 trigger 回退方案。

## 检索 BM25 分支（唯一查询改动）

```sql
SELECT mc.id::text, mc.document_id::text, md.title, mc.embed_text AS excerpt,
       (mc.bm25vector <&> to_bm25query('idx_md_child_bm25', tokenize(?, 'md_tok'))) AS score,
       mc.section, mc.seq_in_parent
FROM md_child_chunk mc JOIN md_documents md ON md.id = mc.document_id
WHERE mc.space_id = ?::uuid AND md.deleted_at IS NULL
  AND mc.bm25vector IS NOT NULL          -- 排除冷启动/回填前的脏行
ORDER BY score ASC LIMIT ?               -- BM25 分为负，越负越相关 → ASC
```

- 用户 query 走 `?` 绑定（防注入）；index / tokenizer 名为服务端配置常量，白名单 `^[A-Za-z_][A-Za-z0-9_]*$` 校验后内联。
- query 经**同一 tokenizer `md_tok`** 切词，与索引侧一致。

## 配置与回退

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `enterprise.kb.md.keyword-mode` | `BM25` | md 关键词算法：`TRGM` / `BM25`。`application.yml` 设 `${MD_KEYWORD_MODE:BM25}`（生效默认 BM25）；代码 `@Value` 兜底值仍是 `TRGM`（属性缺失时） |
| `enterprise.kb.md.bm25-tokenizer` | `md_tok` | 索引与查询共用 tokenizer 名 |
| `enterprise.kb.md.bm25-index` | `idx_md_child_bm25` | vchord_bm25 索引名，`to_bm25query` 按名引用 |

build 跑通后**现已默认走 BM25**（`application.yml` `MD_KEYWORD_MODE` 默认 `BM25`）；线上异常**一键回退** TRGM（`MD_KEYWORD_MODE=TRGM`），无需改代码、无需重建数据（`bm25vector` 列保留即可）。

## 评估

- **种子集**：`kb-search/src/test/resources/md-keyword-eval/frozen-testset.jsonl`，手造 `query → 期望命中 child id` 的 frozen 集（固定、可复现，当前为**模板待运营填充**）。frozen 集永不进任何 prompt/few-shot，避免准确率虚高。
- **harness**：`MdKeywordEvalTest`，`MD_KEYWORD_EVAL=true` 门控，对同组 query 跑 TRGM vs BM25，输出 recall@k / 命中率 / MRR 对照（仿 `DomainRouterEvalTest`）。
- **翻默认条件**：BM25 在种子集上 recall@k 不劣于（目标优于）TRGM。

## 实现状态（✅ 代码已落地 / ⬜ 待运行环境执行）

1. ✅ 换镜像 `tensorchord/vchord-suite:pg16-20260501`
2. ✅ migration 029：扩展 + `bm25vector` 列 + bm25 索引
3. ✅ ingest 代表性语料后跑 `db/manual/md-bm25-build.sql`（建 model/tokenizer/trigger + 回填）—— **已执行（2026-06-04）**
4. ✅ `MdKeywordSearchServiceImpl` 加 BM25 分支 + 开关
5. ⬜ 填评估集 → 跑 `MdKeywordEvalTest`（recall@k 对照，可选）
6. ✅ 运行时走 `BM25`（已确认不再降级 TRGM）

> **更新（2026-06-04）**：`db/manual/md-bm25-build.sql` 已跑通——`tokenizer_catalog.model` / `text_analyzer` / `tokenizer` 三表与 `md_model` / `md_ta` / `md_tok` 已生成，第 3 步完成，BM25 链路真正可用并已确认运行走 BM25。
>
> ~~**本会话实测（2026-05-27，运行镜像 vchord_bm25 0.3.0 / pg_tokenizer 0.1.1）**：029 已 apply 但 `tokenizer_catalog` 三表均 0 行，build 脚本尚未跑，BM25 链路尚未真正可用。~~（已被上方 2026-06-04 更新取代，保留作历史）

## 风险

- **改词表/同义词 = 重 tokenize 整列 + REINDEX**：`bm25vector` 按 tokenizer 配置算好存储；改 model 词表/加同义词后已存向量失效，须 flag=TRGM 保护下重跑回填 `UPDATE` + `REINDEX INDEX idx_md_child_bm25`（仅 md 竖井、可离线）。
- **冷启动**：全新部署 `md_child_chunk` 空，须先 ingest 再建 model；建前 BM25 无数据（`bm25vector` 全 NULL），默认 TRGM 不受影响。
- **新组件成熟度**：vchord_bm25 / pg_tokenizer 较新，换镜像后需回归 md 入库（7/7）与检索单测。
- **种子评估集偏差**：手造集规模小，仅作解锁默认翻转的最低门槛。
- **IK 边界**：无 IK 式 user_dict，硬术语收敛靠 custom model 词表 + 同义词表，命中质量以评估为准。

## 关联文件

| 概念 | 文件 |
|------|------|
| 扩展 + 列 + 索引（migration）| `kb-app/.../db/changelog/029-add-md-bm25.sql` |
| analyzer + model + tokenizer + trigger + 回填（运维脚本）| `kb-app/.../db/manual/md-bm25-build.sql` |
| 检索 BM25 分支 + 开关 | `kb-search/.../service/impl/MdKeywordSearchServiceImpl.java` |
| 配置项 | `kb-app/.../application.yml`（`enterprise.kb.md`）|
| 镜像 | `docker-compose.yml`（`postgres.image`）|
| 评估 harness + 种子集 | `kb-search/.../eval/MdKeywordEvalTest.java`、`.../md-keyword-eval/` |

## 关联页面

- 所属竖井：[[features/markdown-structure-rag]]（结构感知父子索引，本功能升级其关键词路）
- 架构决策：[[decisions/adr-013-md-keyword-bm25]]
- 混合检索：[[ai-rag/hybrid-search]]（RRF k=60）
- 混合检索决策：[[decisions/adr-003-hybrid-search-rrf]]
- 迁移：[[database/migrations]]（029）
