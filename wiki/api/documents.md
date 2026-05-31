# Markdown 文档管理 API

基础路径：`/api/v1/spaces/{spaceId}/md-documents`

控制器：`MdDocumentController`（kb-document 模块）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `GET` | `/` | VIEWER | md 文档列表（分页） |
| `POST` | `/upload` | EDITOR | 上传 md 文档（multipart/form-data） |
| `GET` | `/{documentId}` | VIEWER | md 文档详情 |
| `DELETE` | `/{documentId}` | EDITOR | 软删除 md 文档（同步清除 Milvus 向量 + 资产） |

## 上传说明

- 仅接受 `.md` 文件（结构感知 RAG 竖井）
- 上传后状态为 `PENDING` → `PROCESSING` → `READY` / `FAILED`
- 异步入库经 `MdDocumentIngestionWorker`（virtual thread, `ingestionExecutor`）
- 原始 `.md` 文件存 MinIO（`md_documents.object_key`），ingestion worker 解析前下载到本地临时文件

## 入库流水线

1. 下载 .md → `MarkdownStructureIngestionService.parse()`：H1-H3 切 parent → 段落级 child（small-to-big）；表格双表示；图片走 `MdImageUnderstandingService`（Noop / DashScope 可切换）
2. 写 `md_parent_chunk` / `md_child_chunk` / `md_document_asset`（PG）
3. 向量化 child 的 `embed_text` → 入 Milvus `md_kb_chunks`

详见 [[features/markdown-structure-rag]] · [[features/document-ingestion]]。

## 已退役

原 `/api/v1/spaces/{spaceId}/documents` 端点（`DocumentController`，承载 PDF/DOCX/MD/TXT/HTML 等多格式上传 + 文档关系 + 标签）已随迁移 031 标准 RAG 退役整体删除。
