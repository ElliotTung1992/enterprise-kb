# Markdown 文档摄入功能

## 完整流程

见 [[architecture/data-flow]] — Markdown 文档上传流，与 [[features/markdown-structure-rag]] 的入库章节。

> 当前唯一活跃竖井是 Markdown 结构感知 RAG（small-to-big 父子索引）。原标准竖井支持 PDF / DOCX / DOC / TXT / HTML / Markdown 多格式 + 句子边界分块（512 token / 100 overlap），随迁移 031 整体退役。

## 支持格式

| 格式 | MIME 类型 | 处理方式 |
|------|-----------|---------|
| Markdown | text/markdown, text/x-markdown | `MarkdownStructureIngestionService` 结构化解析 |

最大文件大小：**100MB**

## Markdown 解析策略

`MarkdownStructureIngestionService.parse()`：

- 按 H1-H3 切 parent（整段 section）
- parent 内再切段落级 child（small-to-big；child overlap=0）
- 表格双表示（检索用逐行线性化 `embed_text`，返回用原始 markdown）
- 代码块独立 child
- 图片 → `MdImageUnderstandingService`（Noop 默认 / DashScope 可切换）→ 写 `md_document_asset` + `IMAGE_CAPTION` 类型 child

详见 [[features/markdown-structure-rag]]。

## 异步处理

- `MdDocumentIngestionWorker` 走 `ingestionExecutor`（JDK 21 虚拟线程池）
- 上传接口立即返回 202，前端可轮询文档状态
- 状态字段：`PENDING` → `PROCESSING` → `READY` / `FAILED`

## 摄入失败处理

正文摄入失败时状态置为 `FAILED`，记录错误日志。图片同步处理，任一图片失败则整篇 import 失败（见 [.planning/2026-05-27-markdown-image-rag/](#)）。

## 删除文档

删除 md 文档时，同步清除：
1. Milvus `md_kb_chunks` 中对应的向量
2. PostgreSQL `md_parent_chunk` / `md_child_chunk` / `md_document_asset` 记录
3. MinIO 中 `object_key` 对应的原始 .md 与图片资产

## 相关页面

- [[features/markdown-structure-rag]] — 结构感知 RAG 完整设计
- [[features/markdown-visual-rag-l2]] — 图文 RAG L2 设计（历史，部分理念已并入 md 竖井）
- [[api/documents]] — 上传端点
- [[architecture/data-flow]] — 数据流图
