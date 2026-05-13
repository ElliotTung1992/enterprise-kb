# documents & document_chunks

## documents 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `space_id` | UUID | 所属知识空间 |
| `user_id` | UUID | 上传者 |
| `name` | VARCHAR | 文件名 |
| `file_path` | VARCHAR | 服务器存储路径 |
| `mime_type` | VARCHAR | MIME 类型 |
| `file_size` | BIGINT | 字节数 |
| `status` | VARCHAR | PENDING / PROCESSING / COMPLETED / FAILED |
| `created_at` | TIMESTAMPTZ | |
| `updated_at` | TIMESTAMPTZ | |
| `deleted_at` | TIMESTAMPTZ | 软删除 |

**支持的 MIME 类型**（白名单）：
- `application/pdf`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (DOCX)
- `application/msword` (DOC)
- `text/markdown`, `text/x-markdown`
- `text/plain`, `text/html`

## document_chunks 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `document_id` | UUID FK | 关联 documents |
| `space_id` | UUID | 冗余存储，加速按空间检索 |
| `milvus_id` | VARCHAR | Milvus 中对应向量的 ID |
| `content` | TEXT | 分块原文 |
| `metadata` | JSONB | 额外元数据（页码、段落序号等） |
| `chunk_index` | INT | 在文档中的顺序 |
| `created_at` | TIMESTAMPTZ | |

## 状态流转

```
PENDING → PROCESSING → COMPLETED
                    └→ FAILED
```

状态转换发生在 `DocumentIngestionPipeline` 中，失败时记录错误信息。
