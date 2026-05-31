# 知识空间 API

基础路径：`/api/v1/spaces`

控制器：`SpaceController`（kb-user 模块）。所有接口需要有效 JWT Token；成员管理类接口需 `OWNER` 角色。

## 空间管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `GET` | `/` | 已登录 | 当前用户可见的空间列表 |
| `POST` | `/` | 已登录 | 创建新空间（创建者自动成为 `OWNER`） |
| `GET` | `/{spaceId}` | `VIEWER` | 空间详情 |
| `DELETE` | `/{spaceId}` | `OWNER` | 软删除空间 |

### 创建空间

```json
POST /api/v1/spaces
{ "name": "产品知识库", "description": "..." }
```

## 成员管理

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `GET` | `/{spaceId}/members` | `VIEWER` | 空间成员列表（含角色） |
| `POST` | `/{spaceId}/members` | `OWNER` | 添加成员 |
| `PUT` | `/{spaceId}/members/{userId}` | `OWNER` | 修改成员角色 |
| `DELETE` | `/{spaceId}/members/{userId}` | `OWNER` | 移除成员 |

### 添加 / 修改成员

```json
POST /api/v1/spaces/{spaceId}/members
{ "userId": "uuid", "role": "EDITOR" }
```

`role` 枚举：`OWNER` / `EDITOR` / `VIEWER`（见 [[database/entities/users-spaces]] 权限层级）。

## 权限模型

```
OWNER   ≥ EDITOR ≥ VIEWER
```

由 `SpacePermissionEvaluator` 实现，所有空间内接口用 `@PreAuthorize("hasPermission(#spaceId, 'SPACE', '...')")` 强制校验。

## 相关页面

- [[database/entities/users-spaces]] — `spaces` / `user_space_roles` 表结构
- [[api/auth]] — 登录注册
- [[api/users]] — 用户管理
