# 文档摄入功能

## 完整流程

见 [[architecture/data-flow]] — 文档上传流

## 支持格式

| 格式 | MIME 类型 | 解析方式 |
|------|-----------|---------|
| PDF | application/pdf | PDF 解析器 |
| DOCX | application/vnd.openxmlformats-... | Apache POI |
| DOC | application/msword | Apache POI |
| Markdown | text/markdown, text/x-markdown | 文本解析 |
| Markdown zip | application/zip | 安全解压后解析主 Markdown，并抽取图片/流程图资产 |
| 纯文本 | text/plain | 直接读取 |
| HTML | text/html | HTML 解析 |

最大文件大小：**100MB**

## Markdown 图文扩展

Markdown zip 中的图片、Mermaid 和 PlantUML 流程图按 L2 图文 RAG 方案处理：图片和渲染图存 MinIO，OCR/caption 结果以文本 chunk 进入现有 RAG 检索链路，并支持 asset 级引用。

处理边界：

- zip 是上传容器，只为主 Markdown 创建文档记录。
- 只支持安全相对路径图片，不支持绝对路径、远程 URL 或 base64 data URL。
- 正文 chunk 先完成入库；图片/流程图视觉理解由 worker 异步补充。
- 当前是 L2 文本投影，不做 L3 多模态图片向量检索。

详见 [[features/markdown-visual-rag-l2]]。

## 分块策略

`SentenceAwareChunkingServiceImpl`（句子边界感知）：
- 块大小：512 token
- 重叠：100 token
- 按句子边界分割，避免语义截断

## 异步处理

- 走 `ingestionExecutor`（JDK 21 虚拟线程池）
- 上传接口立即返回，前端可轮询文档状态
- 状态字段：`PENDING` → `PROCESSING` → `READY` / `READY_WITH_PENDING_ASSETS` / `READY_WITH_ASSET_ERRORS` / `FAILED`

## 摄入失败处理

正文摄入失败时状态置为 `FAILED`，记录错误日志。视觉资产失败不会阻塞正文可检索，文档会进入 `READY_WITH_ASSET_ERRORS`。可通过重新处理文档触发全量清理和重新摄入。

## 删除文档

删除文档时，同步清除：
1. Milvus 中对应的向量
2. PostgreSQL `document_chunks` 记录
3. PostgreSQL `document_assets` 记录
4. 文件系统上的原始文件

重新处理 Markdown zip 时，还会先清理旧视觉资产对象和旧检索投影，再重建。
