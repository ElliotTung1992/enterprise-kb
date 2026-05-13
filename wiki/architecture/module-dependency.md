# 模块依赖关系

## 依赖图

```
kb-common
    ├── kb-auth
    │       └── (手动装配到 kb-app，避免反向依赖)
    ├── kb-user
    │       └── SpacePermissionEvaluator
    ├── kb-document
    ├── kb-search
    └── kb-knowledge-graph
            └── 全部汇聚 → kb-app (启动入口)
```

## 各模块职责

| 模块 | 包前缀 | 核心职责 |
|------|--------|---------|
| `kb-common` | `com.enterprise.kb.common` | ApiResponse · KbException · 枚举常量 · SecurityUtils |
| `kb-auth` | `com.enterprise.kb.auth` | JWT 签发/校验 · 登录注册 · RefreshToken |
| `kb-user` | `com.enterprise.kb.user` | 用户管理 · 知识空间 · RBAC 权限评估 |
| `kb-document` | `com.enterprise.kb.document` | 文档上传/解析/分块/向量化 · 文档关系 |
| `kb-search` | `com.enterprise.kb.search` | 混合检索 · 标准/Agentic QA · 会话管理 · AI配置 |
| `kb-knowledge-graph` | `com.enterprise.kb.graph` | 标签树 · 知识图谱 · 自动打标 |
| `kb-app` | `com.enterprise.kb` | 启动入口 · 跨模块 Bean 手动装配 · Liquibase |

## 跨模块依赖陷阱

`AuthServiceImpl` 需要 `UserService`（来自 `kb-user`），如果直接在 `kb-auth` 声明 Bean 会造成 `kb-auth → kb-user` 的反向依赖。

**解决方案**：在 `kb-app/AppConfig.java` 手动 `new AuthServiceImpl(...)` 并注入 `userService::createFromRegister` 等方法引用，子模块间保持单向依赖。
