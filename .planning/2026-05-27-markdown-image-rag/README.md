# Markdown Image RAG (2026-05-27)

`md-documents` 竖井的图片 RAG 落地：标准 Markdown 图片语法解析 → 同步下载 / 视觉理解 → 入库为 `IMAGE_CAPTION` child chunk，并在 parent 展开与 citation 中带出 `assetUrl` / `assetTitle`。

设计源：`docs/design-md-image-rag.md`（本地未入库）。

## 文件

| 文件 | 用途 |
|------|------|
| [task_plan.md](task_plan.md) | 目标、成功标准、7 个 Phase 验收点、关键决策、踩坑表 |
| [progress.md](progress.md) | 按日推进记录（实现 → 两次测试失败 → 修复 → 全绿） |
| [findings.md](findings.md) | 启动前对现有 md 入库/检索/citation 形状的勘察笔记 |

## 状态

Phase 1–7 全部完成。最终一轮 `mvn test -pl kb-document,kb-search -am -DskipTests=false`：120 tests / 0 failures / 0 errors / 2 skipped。

## 关键决策摘要

- 仅 `md-documents` 竖井，图片走 MinIO 固定 endpoint+bucket 的完整 URL
- 图片处理同步执行，任一图片失败整篇 import 失败
- 新建 `md_document_assets`（迁移 030），不复用 `document_assets`
- 每个图片引用 → 一条 asset + 一条 `IMAGE_CAPTION` child；同文档内同 objectKey 复用视觉理解结果但 asset/child 各算一条
- 只认标准 Markdown `![]()`，HTML `<img>` 与代码块内图片忽略
- 支持 MIME：PNG / JPEG / WebP；默认上限 50 张 / 单张 10 MB

## 两次测试踩坑

1. macOS 把临时 `.img` 文件识别为 `application/x-apple-diskimage` → MIME 检测改为优先看 objectKey 后缀，再回退 `Files.probeContentType`
2. 尾部 orphan 文本被合并回上一段图片切片、抹掉 asset 关联 → packing 阶段对图片切片关闭尾部合并，保持图片 child 独立
