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
| 纯文本 | text/plain | 直接读取 |
| HTML | text/html | HTML 解析 |

最大文件大小：**100MB**

## 分块策略

`SentenceAwareChunkingServiceImpl`（句子边界感知）：
- 块大小：512 token
- 重叠：100 token
- 按句子边界分割，避免语义截断

## 异步处理

- 走 `ingestionExecutor`（JDK 21 虚拟线程池）
- 上传接口立即返回，前端可轮询文档状态
- 状态字段：`PENDING` → `PROCESSING` → `COMPLETED` / `FAILED`

## 摄入失败处理

状态置为 `FAILED`，记录错误日志。可通过重新上传文档触发重新摄入。

## 删除文档

删除文档时，同步清除：
1. Milvus 中对应的向量
2. PostgreSQL `document_chunks` 记录
3. 文件系统上的原始文件
