# 预期结果

使用 `sample.md` 回放 Markdown 图片 RAG 时，预期如下：

| 项 | 预期 |
|---|---|
| 图片资产数量 | `2` |
| 视觉模型调用次数 | `2` |
| objectKey | `md-assets/replay/architecture.png`、`md-assets/replay/dashboard.png` |
| child contentType | 至少包含两个 `IMAGE_CAPTION` |
| enhancedContent | `md_parent_chunk.content` 中保留原始图片语法，并追加 `[图片说明]...[/图片说明]` |
| 代码块图片 | `ignored.png` 不生成资产，不生成图片 child，不调用视觉模型 |
| vector metadata | 图片 child 的 metadata 包含 `contentType=IMAGE_CAPTION`、`assetId`、`assetUrl`、`objectKey` |

关键问答回放：

| 问题 | 预期依据 |
|---|---|
| “系统架构图里有哪些组件？” | 命中 `architecture.png` 的图片说明和 parent enhancedContent |
| “监控面板用来观察什么？” | 命中 `dashboard.png` 的图片说明或其后续文本 |
| “代码块里的 ignored.png 是真实图片吗？” | 不应把代码块内图片作为可检索图片资产 |
