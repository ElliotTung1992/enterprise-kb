# users、spaces、权限

## users 表

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `username` | VARCHAR | 唯一，登录标识 |
| `password_hash` | VARCHAR | BCrypt 哈希 |
| `email` | VARCHAR | 可选 |
| `active` | BOOLEAN | 账户是否启用 |
| `created_at` | TIMESTAMPTZ | |
| `deleted_at` | TIMESTAMPTZ | 软删除 |

## spaces 表（知识空间）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | |
| `name` | VARCHAR | 空间名称 |
| `description` | TEXT | 描述 |
| `owner_id` | UUID | 创建者 |
| `created_at` | TIMESTAMPTZ | |
| `deleted_at` | TIMESTAMPTZ | |

## user_space_roles 表（RBAC）

| 字段 | 类型 | 说明 |
|------|------|------|
| `user_id` | UUID | |
| `space_id` | UUID | |
| `role` | VARCHAR | OWNER / EDITOR / VIEWER |

## 权限层级

```
OWNER   ≥ EDITOR ≥ VIEWER
```

- **OWNER**：可管理成员、删除空间
- **EDITOR**：可上传 / 删除 md 文档
- **VIEWER**：可搜索、问答（只读）

> 原"管理标签"权限随 `kb-knowledge-graph` 模块与 `tags` 表的退役（迁移 031）一并失效。

## 权限检查方式

```java
@PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
```

由 `SpacePermissionEvaluator` 实现，查询 `user_space_roles` 表。
