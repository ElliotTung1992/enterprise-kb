# 文档管理 API

基础路径：`/api/v1/spaces/{spaceId}/documents`

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `POST` | `/` | EDITOR | 上传文档（multipart/form-data） |
| `GET` | `/` | VIEWER | 文档列表（分页） |
| `GET` | `/{id}` | VIEWER | 文档详情 |
| `DELETE` | `/{id}` | EDITOR | 软删除文档（同步清除 Milvus 向量） |
| `POST` | `/{id}/relations` | EDITOR | 添加文档关系 |
| `GET` | `/{id}/relations` | VIEWER | 查询文档关系 |

## 上传说明

- 最大文件：100MB（配置 `enterprise.kb.document.max-file-size-mb`）
- 支持 MIME 类型：PDF、DOCX、DOC、MD、TXT、HTML
- 上传后文档状态为 `PENDING`，后台异步摄入
- 摄入完成后状态变为 `COMPLETED` 或 `FAILED`

## 文档关系类型

`RelationType` 枚举（`kb-common`）：
- `RELATED` — 相关
- `PREREQUISITE` — 前置
- `CONTRADICTS` — 矛盾/冲突
