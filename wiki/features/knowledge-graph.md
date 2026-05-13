# 知识图谱功能

## 标签管理

- 标签树结构（`parent_id` 自引用）
- 按空间隔离
- 支持手动创建和 AI 自动打标

## 自动打标

`AutoTaggingService`：文档摄入完成后，LLM 分析文档内容，自动：
1. 识别文档主题关键词
2. 匹配/创建对应标签
3. 建立文档-标签关联（`auto_detected=true`）

## 文档关系

`DocumentRelationService`：手动建立文档间的语义关系：
- `RELATED`：相关
- `PREREQUISITE`：前置知识
- `CONTRADICTS`：内容矛盾

## 图谱 API

`TagController` 提供：
- 标签树查询（带文档数统计）
- 图谱数据查询（节点=文档，边=关系+标签）
- 文档按标签筛选

## 相关页面

- [[database/entities/tags-graph]]
- [[api/documents]] — 文档关系接口
