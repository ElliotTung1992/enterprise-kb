# Markdown 结构感知 RAG 验收报告

> 对应功能：[[features/markdown-structure-rag]]
> 完整验收文档：`docs/acceptance-md-structure-rag.md`（含逐条勾选表、curl 示例、核验 SQL、签字栏）
> 验收范围：纯 `.md` 文件的入库与问答竖井，与标准 RAG 完全平行的全新链路。

## 摘要

| 项 | 内容 |
|---|---|
| 代码改动量 | 43 个文件，+3575 行 |
| 新增迁移 | `027-create-markdown-rag.sql`、`028-md-documents-object-key.sql` |
| 新增 Milvus 集合 | `md_kb_chunks`（1536 / COSINE / IVF_FLAT）|
| 新增 REST 端点 | 文档 4 个 + 问答 3 个（见 [[features/markdown-structure-rag]]）|
| 标准竖井影响 | 零代码改动（仅在 `AppConfig` 把自动配置的 `vectorStore` 标 primary）|

## 验收清单（分组）

完整「验收点 → 方法 → 预期」表见 `docs/acceptance-md-structure-rag.md` §3，分六组：

- **A 数据层**：三表 / 级联 / `pg_trgm` 索引 / `md_kb_chunks` 集合；`kb_chunks` 仍独立。
- **B 入库**：上传返回 202、状态机 `PENDING→PROCESSING→READY/FAILED`、H1-H3 parent 切分、child 512/64 打包、表格双表示、重新入库幂等清理、`chunk_count` 回填。
- **C 检索问答**：同步 / 流式 / Agentic 三端点、RRF 不去重、parent 折叠去重（≤5）、多窗节选（簇间 `……（中间略）……`）、表格行组补表头、空检索兜底不编造、prompt 注入防护、多轮会话落库。
- **D 删除清理**：文档删除清向量 + `SpaceDeletedEvent` 监听清理 + 标准竖井不受影响。
- **E 权限**：上传/删除 EDITOR，查询/问答 VIEWER。
- **F 配置项**：8 个默认值核对（`md-collection` / `chunking.markdown.*` / `md-parent-expansion.*`）。

## 自动化测试

| 测试 | 覆盖 | 结果 |
|---|---|---|
| `MarkdownStructureIngestionServiceImplTest` | H1-H3 切分 + 面包屑前置、表格线性化、行内竖线非表格、大表格按行切、代码块原子性、超长列表按项切、贪心打包孤儿方向（C17）| ✅ Tests run: 7, Failures: 0, Errors: 0（BUILD SUCCESS）|

运行（注意 `-am` 须加 `failIfNoSpecifiedTests=false`，否则在 `kb-common` 处因无匹配用例中断）：

```bash
mvn test -pl kb-document -am \
  -Dtest=MarkdownStructureIngestionServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 待验收 / 遗留

- 检索、parent 展开、Agentic 暂无自动化测试，需按 §3 C/D 组手动联调（需起 Milvus/PG/Redis/MinIO）。
- 端到端 & 多轮会话回归未跑。架构决策见 [[decisions/adr-012-markdown-structure-rag]]。

## 本期不做（不在验收范围）

- 非 `.md` 格式（走标准竖井）。
- md 文档参与知识图谱打标 / 文档关系。
- 图片/流程图视觉理解、OCR、表格 LLM 摘要 child（图文交给 [[features/markdown-visual-rag-l2]]）。
- 历史 `.md`（已走标准链）自动回填到 md 竖井。
