# 用户 API

基础路径：`/api/v1/users`

控制器：`UserController`（kb-user 模块）。

## 端点

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| `GET` | `/` | 已登录 | 用户列表（支持按 username 模糊搜索，用于添加空间成员时挑人） |
| `GET` | `/{userId}` | 已登录 | 用户详情 |
| `POST` | `/` | 管理员 | 创建用户（普通注册走 [[api/auth]] `/register`） |

### 查询用户列表

```
GET /api/v1/users?keyword=alice
```

返回去敏后的 `UserSummary` 列表（不含密码哈希）。

## 相关页面

- [[api/auth]] — 注册 / 登录 / Token 刷新
- [[api/spaces]] — 把用户加入空间
- [[database/entities/users-spaces]] — 表结构
