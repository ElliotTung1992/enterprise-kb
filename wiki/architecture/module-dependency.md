# 模块依赖关系

## 依赖图

```
kb-common
    ├── kb-auth
    │       └── （手动装配到 kb-app，避免反向依赖）
    ├── kb-user
    │       └── SpacePermissionEvaluator
    ├── kb-document
    └── kb-search
            └── 全部汇聚 → kb-app（启动入口）
```

> 原 `kb-knowledge-graph` 模块（标签树 / 自动打标 / 文档关系）已随迁移 031 标准 RAG 退役整体删除。

## 各模块职责

| 模块 | 包前缀 | 核心职责 |
|------|--------|---------|
| `kb-common` | `com.enterprise.kb.common` | `ApiResponse` · `KbException` · 枚举常量 · `SecurityUtils` · `TracingSupport` / `TracingContextHolder`（共享 tracing 工具） |
| `kb-auth` | `com.enterprise.kb.auth` | JWT 签发/校验 · 登录注册 · `RefreshToken` |
| `kb-user` | `com.enterprise.kb.user` | 用户管理 · 知识空间 · RBAC 权限评估 |
| `kb-document` | `com.enterprise.kb.document` | Markdown 文档上传 · 结构化解析 · 图片理解 · 向量入库（md 竖井） |
| `kb-search` | `com.enterprise.kb.search` | Markdown 混合检索 · 标准 / Agentic md QA · 客服助手 · 投诉工作流 · Eval · 会话管理 · AI 配置 |
| `kb-app` | `com.enterprise.kb` | 启动入口 · 跨模块 Bean 手动装配 · Liquibase |

## 跨模块依赖陷阱

`AuthServiceImpl` 需要 `UserService`（来自 `kb-user`），如果直接在 `kb-auth` 声明 Bean 会造成 `kb-auth → kb-user` 的反向依赖。

**解决方案**：在 `kb-app/AppConfig.java` 手动 `new AuthServiceImpl(...)` 并注入 `userService::createFromRegister` 等方法引用，子模块间保持单向依赖。同样地，`SpacePermissionEvaluator`、`MethodSecurityExpressionHandler`、`ingestionExecutor`、`milvusClient` / `mdVectorStore` 也都在 `AppConfig` 集中装配。

## 相关页面

- [[decisions/adr-001-multi-module-maven]] — 多模块 Maven 决策
- [[architecture/overview]] — 整体分层
