# 认证 API

基础路径：`/api/v1/auth`（无需鉴权）

## POST `/register` — 注册

```json
{"username": "alice", "password": "secure123"}
```

## POST `/login` — 登录

```json
{"username": "alice", "password": "secure123"}
```

**响应**
```json
{
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "xxx",
    "expiresIn": 3600
  }
}
```

## POST `/refresh` — 刷新 Token

```json
{"refreshToken": "xxx"}
```

## POST `/change-password` — 修改密码（需登录）

```json
{"oldPassword": "xxx", "newPassword": "yyy"}
```

## JWT 配置

| 配置项 | 默认值 |
|--------|--------|
| AccessToken 有效期 | 60 分钟 |
| RefreshToken 有效期 | 30 天 |
| 密钥长度 | ≥ 32 字节，启动时校验 |

所有需要鉴权的接口：请求头 `Authorization: Bearer {accessToken}`

## 相关页面

- [[api/users]] — 用户管理
- [[api/spaces]] — 知识空间与 RBAC（接口侧的权限校验入口）
- [[database/entities/users-spaces]] — `users` / `refresh_tokens` 表结构
