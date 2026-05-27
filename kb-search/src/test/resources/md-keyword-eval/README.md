# md 关键词检索评估集（TRGM vs BM25）

仿 `intent-eval/` 体例的离线评估，用于设计文档 `docs/design-md-keyword-bm25.md` §9 的「翻默认门控」：
**BM25 在种子集上 recall@k 不劣于（目标优于）TRGM**，达标后才把 `MD_KEYWORD_MODE` 默认从 `TRGM` 翻 `BM25`。

## 文件

| 文件 | 说明 |
|------|------|
| `frozen-testset.jsonl` | 固定评估集，每行一个 case。仓库内现为模板示例，需用真实数据替换。 |

## case 字段

```json
{"query": "退货流程怎么走", "spaceId": "<空间UUID>", "relevantChildIds": ["<md_child_chunk.id>", ...], "topK": 10}
```

- `query`：用户查询文本。
- `spaceId`：检索所在知识空间的 UUID。
- `relevantChildIds`：人工判定该 query 应命中的 `md_child_chunk.id`（可多个）。
- `topK`：取前 k 个命中算 recall，默认 10。
- 支持 `//` 开头的整行注释与空行（会被跳过）。

## 如何获得 relevantChildIds

针对某个空间、某条 query，先人工确认哪段内容是正确答案，再查它的 child id：

```sql
SELECT mc.id, mc.section, left(mc.embed_text, 80) AS preview
FROM md_child_chunk mc
JOIN md_documents md ON md.id = mc.document_id
WHERE mc.space_id = '<空间UUID>' AND md.deleted_at IS NULL
  AND mc.embed_text ILIKE '%关键片段%'
ORDER BY mc.document_id, mc.seq_in_parent;
```

把判定为正确答案的 `id` 填进 `relevantChildIds`。建议 10–30 条覆盖典型 query（术语/产品名/中英混排/长 query）。

## 运行

前置：Postgres 已换 `vchord-suite:pg16` 镜像、migration 029 已 apply、语料已 ingest、`db/manual/md-bm25-build.sql` 已执行（model/tokenizer/trigger 就位、存量已回填）。

```bash
MD_KEYWORD_EVAL=true \
  MD_EVAL_DB_URL=jdbc:postgresql://127.0.0.1:5432/enterprise_kb \
  MD_EVAL_DB_USER=kb_user MD_EVAL_DB_PASSWORD=xxx \
  mvn test -pl kb-search -Dtest=MdKeywordEvalTest
```

输出 TRGM vs BM25 的 recall@k / 命中率 / MRR 对照记分卡。`recall@k` 列即门控指标。
