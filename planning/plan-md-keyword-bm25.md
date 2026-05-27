# 实施计划：md 关键词检索升级 BM25（VectorChord-bm25 + pg_tokenizer）

> 配套设计：`docs/design-md-keyword-bm25.md`
> 本计划已**就地核实**设计 §12 的外部事实（见 Phase 0），并据此修正了设计中与现实不符的前提。

---

## 核实后定稿（v2，覆盖下文 Phase 0/2/3 的旧表述）

读官方 `pg_tokenizer.rs` 原文（`docs/03-examples.md`、`05-text-analyzer.md`、`06-model.md`，经 `gh api` 取原文）后，**两条设计前提被推翻**，定稿如下：

1. **❌ jieba 无 `user_dict`。** 设计 §3.2/§3.3 选型的决定性理由（"jieba 可挂自定义词典 = IK 等价能力"）不成立——pg_tokenizer 的 jieba 仅 `[pre_tokenizer.jieba]` 一项，无挂词典文件入口。自定义术语能力改由：**自训练 custom model 的语料词表** + `create_synonym`（同义词）+ `create_stopwords`（停用词）+ `pg_dict`。
2. **❌ jieba 必须配「从语料训练的 custom model」**，不能用预训练模型。`create_custom_model` 从 `md_child_chunk.embed_text` 学词表 ⇒ migration **非纯 SQL 可复现**、**空表冷启动**。
3. **入库写入方式 = 方式 1（trigger 自动填充，已与用户确认）。** `bm25vector` 由 DB 端 trigger 在 model 建好后自动维护新行 ⇒ **`MdChildChunkMapper.xml` 不改**（撤销下文 Phase 3 的 mapper 改动）。
4. **tokenizer 路线 = jieba + 自训练 model（已与用户确认）**，忠于设计中文分词意图。

**据此的最终落地形态：**
- **migration 029**（对空表安全、可复现）：仅 `CREATE EXTENSION` + `ADD COLUMN bm25vector`(可空) + `CREATE INDEX USING bm25`。**不含** analyzer/model/tokenizer/trigger。
- **运维脚本 `db/manual/md-bm25-build.sql`**（语料 ingest 后手动跑）：`create_text_analyzer([pre_tokenizer.jieba])` → `create_custom_model_tokenizer_and_trigger`（model+tokenizer+trigger 一次建好，target=`bm25vector`）→ 回填 `UPDATE`。附手动 trigger 回退方案。
- **`MdChildChunkMapper.xml`：不动**（方式 1）。
- **`MdKeywordSearchServiceImpl`：BM25 分支 + `keyword-mode` 开关**（唯一查询改动格，已实现）。
- **`application.yml`：`enterprise.kb.md.{keyword-mode,bm25-tokenizer,bm25-index}`**（已实现）。
- **`docker-compose.yml`：`tensorchord/vchord-suite:pg16-20260501`**（已实现）。
- **唯一仍需对运行镜像 live-verify**：函数是否需 `tokenizer_catalog.` 前缀、`create_custom_model_tokenizer_and_trigger` 的 target_column 是否支持 `bm25vector`（不支持则用脚本内回退方案）。

> 下文 Phase 0–7 为初版分析，**凡与本节冲突，以本节为准**（尤其 Phase 2 的"纯 jieba 不带 user_dict"、Phase 3 的"改 mapper"两处已废止）。

---

## Phase 0：文档发现与外部事实核实（设计 §12）

设计明确要求"落地前现场核实，不照记忆写"。以下结论来自官方仓库 / 文档 / Docker Hub 实查（2026-05 当月）：

### 已核实的允许 API（Allowed APIs）

| 事项 | 核实结论（copy-ready） | 来源 |
|------|------------------------|------|
| 扩展安装 | `CREATE EXTENSION IF NOT EXISTS pg_tokenizer CASCADE;` `CREATE EXTENSION IF NOT EXISTS vchord_bm25 CASCADE;` | VectorChord-bm25 README / pgEdge usage |
| 列类型 | `ALTER TABLE ... ADD COLUMN bm25vector bm25vector;` | README |
| 切词 | `tokenize(text, '<tokenizer_name>')::bm25vector` —— 是 **DB 端函数**，不是应用层算 | README / pgEdge |
| 建索引 | `CREATE INDEX <name> ON <tbl> USING bm25 (<col> bm25_ops);` | README / pgEdge |
| 查询 | `<col> <&> to_bm25query('<index_name>', tokenize('<query>', '<tokenizer>')) AS score ... ORDER BY score`（**升序**：BM25 分为负，越负越相关） | EDB using / pgEdge |
| tokenizer 定义 | `SELECT create_text_analyzer('<ta>', $$ ...TOML... $$);` 再 `SELECT create_tokenizer('<tok>', $$ text_analyzer="<ta>" model="..." $$);` | pg_tokenizer.rs docs/04-usage.md |
| jieba 分词 | text_analyzer 的 `pre_tokenizer` 设为 jieba；停用词/同义词走 `[[token_filters]]`；自定义词典走 jieba 的 `user_dict` | pg_tokenizer.rs README + blog |
| **Docker 镜像** | **`tensorchord/vchord-suite:pg16-latest`（已发布 pg16 标签，最新 `pg16-20260501`，amd64+arm64，含全部三扩展）** | Docker Hub tags 实查 |

### 对设计措辞的两处修正（基于实查）

1. **设计 §6 说"应用层计算并写入 `bm25vector`"——不准确。** `tokenize()` 是 Postgres 函数，必须 DB 端执行。
   正确落点：**`MdChildChunkMapper.xml` 的 `insertBatch` INSERT 列里加 `bm25vector = tokenize(#{...embedText}, '<tok>')`**，
   而不是改 `MarkdownStructureIngestionServiceImpl`（该类只做 `parse()`，不碰 DB，符合项目"Mapper 只做 CRUD"分层）。
2. **设计假设"换镜像成本相当、PG16 可用 pg_tokenizer"——实查为真。** `vchord-suite` 确有 pg16 标签，
   且打包了 pg_tokenizer（自定义词典能力在 pg16 上可用），不需要把 Postgres 升到 PG17。设计的核心选型成立。

### 仍需对运行镜像最终确认的一项（唯一 live-verify 点）

- **tokenizer 配置的精确 TOML 形状**：各来源对 jieba 的写法有分歧（`pre_tokenizer = "jieba"` 标量式 vs `[pre_tokenizer.jieba]` 表式 vs `[[pre_tokenizers]]` 数组式），且 `user_dict` 的确切键名与"文件路径 vs 内联词表"未被任一可访问文档钉死。
  → migration 029 的 `create_text_analyzer`/`create_tokenizer` 块按本计划写出**最可靠形态 + 内联注释**，但**首次 apply 前必须对运行中的 `vchord-suite:pg16` 镜像 `psql` 验证一次**（`\df create_text_analyzer`、官方镜像内 docs、或试跑 `SELECT tokenize('测试中文','<tok>')`）。其余 DDL/查询语法均已确认，无需再验。

### 反模式守卫（不要做）

- ❌ 不要在应用层（Java）自算 BM25 或自己做切词——切词与打分全在 DB。
- ❌ 不要把 `section` 面包屑掺进 `embed_text` 再喂 BM25（破坏 A/B：必须只变算法不变输入）。
- ❌ 不要动标准竖井 `document_chunks` / `KeywordSearchServiceImpl`（非 md 场景下线）。
- ❌ 不要把向量迁出 Milvus（dense 留 `md_kb_chunks`，scope creep）。
- ❌ 不要按记忆写 tokenizer TOML——见上"唯一 live-verify 点"。
- ❌ `ORDER BY score DESC` 是错的——BM25 分为负，必须 `ASC`。

---

## 影响面与不变量

- **唯一查询代码改动格**：`MdKeywordSearchServiceImpl.search()`（trgm 分支 + bm25 分支，开关切换）。
- **唯一入库改动格**：`MdChildChunkMapper.xml` 的 `insertBatch`（多加一列 `bm25vector`）。
- **不动**：`MdHybridSearchServiceImpl`（RRF 按排名融合，对分数符号/量纲无感）、`SearchHit/SearchResponse` 契约、`MdVectorSearchServiceImpl`（dense 留 Milvus）、`MdParentExpansionServiceImpl`、`MdQnAServiceImpl`。
- **现有单测不受影响**：`MarkdownStructureIngestionServiceImplTest` 只测 `parse()`（纯函数，无 DB）；无任何 md mapper 的 DB 集成测试。

---

## Phase 1：基础设施——换 Postgres 镜像（环境步骤，需运行环境）

**做什么（copy from Phase 0 实查）**
- `docker-compose.yml`：`postgres.image: postgres:16` → `tensorchord/vchord-suite:pg16-20260501`（钉版本，不用 `-latest`，保可复现）。
- 现有 `docker/init/postgres-init.sql` 与数据卷不动。

**验证清单**
- [ ] `docker compose --env-file .env up -d postgres` 起得来。
- [ ] `psql ... -c "SELECT version();"` 仍是 PG16。
- [ ] 现有 28 条 Liquibase 迁移（001–028）在新镜像上照常 apply（应用启动日志无 changelog 报错）。
- [ ] `CREATE EXTENSION pg_tokenizer CASCADE; CREATE EXTENSION vchord_bm25 CASCADE;` 成功；`pg_trgm` 仍在（标准竖井 015 依赖）。

**反模式守卫**：用 `-latest` 会让构建不可复现——必须钉具体日期标签。

---

## Phase 2：migration 029（DDL + tokenizer + 列 + 索引 + 回填）

**做什么**
- 新建 `kb-app/src/main/resources/db/changelog/029-add-md-bm25.sql`，在 master changelog 末尾 include。
- 内容：① `CREATE EXTENSION` ② `create_text_analyzer` + `create_tokenizer`（jieba，首版**不带 user_dict**，理由见下）③ `ALTER TABLE md_child_chunk ADD COLUMN bm25vector bm25vector` ④ `CREATE INDEX idx_md_child_bm25 ... USING bm25` ⑤ 一条回填 `UPDATE`。
- Liquibase 用 `--liquibase formatted sql`，且对 `create_tokenizer` 这类**重复执行会报错**的语句加 `runOnChange`/幂等保护或 `DROP ... IF EXISTS` 前置。

**关于首版种子词典——本计划相对设计的明确取舍**
- 设计要求"首版即带种子词典"。但种子词典须从**现有 md 语料**抽取，而语料在 DB/对象存储里，**本会话无运行环境无法抽取**；且 `user_dict` 的确切机制（文件路径 vs 内联）是 Phase 0 标注的唯一 live-verify 点，照记忆写违反设计 §12。
- **决定**：v1 迁移用**纯 jieba（不挂 user_dict）**先打通；种子词典作为 Phase 6 的环境内跟进项（抽语料 → 加 user_dict → flag=TRGM 保护下重跑回填+重建索引）。此代价设计 §13 已显式接受（仅 md 竖井、可控、flag 保护）。

**验证清单**
- [ ] migration 029 在新镜像上 apply 成功，`\d md_child_chunk` 见 `bm25vector` 列与 bm25 索引。
- [ ] `SELECT tokenize('测试中文分词效果','md_jieba');` 返回非空 bm25vector（**这一步同时验证 Phase 0 的 TOML 形状**）。
- [ ] 回填 `UPDATE` 后 `SELECT count(*) FROM md_child_chunk WHERE bm25vector IS NULL;` 为 0（或仅新空表 0 行）。

**反模式守卫**：migration 必须可重复 apply（Liquibase 同一 changeset 只跑一次，但本地反复重建 DB 时 `create_tokenizer` 重名会报错——加 IF EXISTS 守卫）。

---

## Phase 3：入库管线写入 bm25vector（mapper）

**做什么**
- `MdChildChunkMapper.xml` `insertBatch`：列清单加 `bm25vector`，每行值加 `tokenize(#{chunk.embedText}, 'md_jieba')`。
- tokenizer 名从配置取（见 Phase 5），但 mapper XML 无法注入 @Value——用**固定常量名 `md_jieba`** 写死在 SQL（它是 migration 建的固定对象名，非用户输入，与 flag 无关，入库恒写）。
- 入库恒写 bm25vector（即便 flag=TRGM），这样 flag 一翻 BM25 即有数据、可做影子 A/B——与设计 §4 一致。

**验证清单**
- [ ] `mvn install -pl kb-document -am -DskipTests` 编译通过。
- [ ] 重新入库一篇 md 后 `SELECT id, bm25vector IS NOT NULL FROM md_child_chunk LIMIT 5;` 全为 true。
- [ ] 现有 `MarkdownStructureIngestionServiceImplTest` 仍 7/7（不碰 DB，必然不受影响）。

**反模式守卫**：不要把 `bm25vector` 写进 `MdChildChunk` model / resultMap 的 SELECT 回读字段（它是写入侧派生列，检索 SQL 单独算 score，不回查向量本身）。

---

## Phase 4：检索 BM25 分支 + keyword-mode 开关（唯一查询改动格）

**做什么**
- `MdKeywordSearchServiceImpl`：注入 `@Value enterprise.kb.md.keyword-mode`（TRGM/BM25）、`bm25-tokenizer`、`bm25-index`。
- `search()` 按 mode 选 SQL 分支：
  - TRGM 分支 = 现状原样保留。
  - BM25 分支 = `mc.bm25vector <&> to_bm25query('<index>', tokenize(?, '<tokenizer>')) AS score ... WHERE bm25vector IS NOT NULL ORDER BY score ASC LIMIT ?`，用户 query 走 `?` 绑定（防注入），index/tokenizer 名是配置常量、以 `String.format` 内联（非 MyBatis、非用户输入）。
  - 两分支都返回同形 `SearchHit`（`MD_CHILD` 粒度、字段不变），`searchMode` 标 `MD_KEYWORD`。

**验证清单**
- [ ] `mvn install -pl kb-search -am -DskipTests` 编译通过。
- [ ] flag=TRGM（默认）行为与改动前逐字节一致（grep 确认 TRGM 分支 SQL 未变）。
- [ ] flag=BM25 时手测一条中文 query 返回有序命中、RRF 后混合结果正常（`MdHybridSearchServiceImpl` 零改动）。

**反模式守卫**：`ORDER BY score ASC`（不是 DESC）；BM25 分支必须 `WHERE bm25vector IS NOT NULL`（防回填前的脏行）。

---

## Phase 5：配置项

**做什么**——`application.yml` `enterprise.kb` 下加 `md:` 块：
```yaml
    md:
      keyword-mode: ${MD_KEYWORD_MODE:TRGM}      # TRGM(默认) / BM25，一键回退
      bm25-tokenizer: ${MD_BM25_TOKENIZER:md_jieba}
      bm25-index: ${MD_BM25_INDEX:idx_md_child_bm25}
```

**验证清单**
- [ ] 应用以默认配置启动，`keyword-mode` 解析为 TRGM。

---

## Phase 6：评估（仿 intent-eval 体例，环境内跑）

**做什么**
- 资源目录 `kb-search/src/test/resources/md-keyword-eval/`：`frozen-testset.jsonl`（模板 10–30 条 `query → 期望命中 child id/section`）+ `README.md`。
- `kb-search/src/test/java/.../eval/MdKeywordEvalTest.java`：仿 `DomainRouterEvalTest`，`@EnabledIfEnvironmentVariable(named="MD_KEYWORD_EVAL", matches="true")`，对同一组 query 跑 TRGM 与 BM25，输出 recall@k / MRR 对照记分卡。
- 种子词典跟进（设计要求、本期环境内补）：抽语料高频 OOV 术语 → 写 user_dict → flag=TRGM 下重跑 029 回填 UPDATE + 重建 bm25 索引。

**验证清单 / 翻默认门控**
- [ ] `MD_KEYWORD_EVAL=true mvn test -pl kb-search -Dtest=MdKeywordEvalTest` 跑通，打印 TRGM vs BM25 记分卡。
- [ ] BM25 recall@k 不劣于（目标优于）TRGM → 才把 `MD_KEYWORD_MODE` 默认翻 BM25。

---

## Phase 7：最终验证

- [ ] 全项目 `mvn install -DskipTests` 编译通过。
- [ ] `grep -rn "ORDER BY score DESC" MdKeywordSearchServiceImpl` 仅出现在 TRGM 分支（BM25 分支为 ASC）。
- [ ] master changelog 含 029；`docker-compose.yml` 镜像已钉版本。
- [ ] 默认行为（flag=TRGM）与上线前一致；BM25 仅在评估达标后翻默认。

---

## 本会话可交付 vs 需运行环境

| 可在本会话产出并编译验证 | 需运行环境（Docker/PG/语料） |
|---|---|
| migration 029 SQL、master include | 镜像拉取 + 029 apply + tokenize 验证（含 TOML live-verify） |
| `MdChildChunkMapper.xml` 改动 | 回填 UPDATE 实跑 |
| `MdKeywordSearchServiceImpl` BM25 分支 + 开关 | flag=BM25 端到端手测 |
| `application.yml` 配置 | 评估集标注 + `MdKeywordEvalTest` 实跑 |
| `docker-compose.yml` 镜像钉版本 | 种子词典抽取 + 重建 |
| 评估 harness + frozen 模板 + README | 翻默认 |
