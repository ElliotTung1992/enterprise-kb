# md 关键词检索升级 BM25（2026-05-28）

升级 md 竖井**关键词那一路**：pg_trgm → 真正 BM25（VectorChord-bm25 + pg_tokenizer jieba）。改动只在 `MdKeywordSearchServiceImpl` 一个 method——RRF 按排名融合，BM25 负分免归一化。

三件套：`md_ta`（jieba 切词）→ `md_model`（词→ID **冻结**词表，从 `embed_text` 学）→ `md_tok`。`bm25vector`={词ID:TF}（严格升序，TF 是值不是维度）；DF/IDF 在 `USING bm25` 倒排索引、`to_bm25query` 查询时现算。

## 状态

代码已落地、编译通过、md 入库 7/7。**运行态 build 脚本未跑**——实测 `tokenizer_catalog.model/text_analyzer/tokenizer` 三表 0 行，`md_model` 等尚不存在，BM25 链路未真正可用，默认仍 TRGM。`db/manual/md-bm25-build.sql` 须语料 ingest 后手动跑。

## 文件

- [plan.md](plan.md) — Phase 0 外部事实核实（推翻设计 §3.2/§3.3 两条前提）+ 定稿 v2 + 分阶段实施

## 关联 wiki

- [[features/md-keyword-bm25]]
- [[decisions/adr-013-md-keyword-bm25]]
- 设计文档：`docs/md-structure-rag.md` 第三部分「关键词检索升级 BM25 子设计」（原 `docs/design-md-keyword-bm25.md` 已并入合订本）
