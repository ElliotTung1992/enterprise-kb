# tags & 知识图谱

## tags 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `space_id` | UUID | 所属知识空间 |
| `name` | VARCHAR | 标签名 |
| `parent_id` | UUID (nullable) | 父标签，支持树形结构 |
| `auto_detected` | BOOLEAN | 是否由 AI 自动打标 |
| `created_at` | TIMESTAMPTZ | |
| `deleted_at` | TIMESTAMPTZ | |

## document_tags 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `document_id` | UUID | |
| `tag_id` | UUID | |

## document_relations 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `source_id` | UUID | 源文档 |
| `target_id` | UUID | 目标文档 |
| `relation_type` | VARCHAR | RELATED / PREREQUISITE / CONTRADICTS 等 |
| `created_at` | TIMESTAMPTZ | |

## 自动打标

`AutoTaggingService` 在文档摄入完成后，用 LLM 分析文档内容，自动创建/关联标签（`auto_detected=true`）。

## 知识图谱

`KnowledgeGraphService` 查询文档-标签-文档关系，返回供前端可视化的图数据（`GraphDto`）。
