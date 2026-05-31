---
created: 2026-04-01
tags: [adr, architecture, maven, multi-module]
---

# ADR-001: 多模块 Maven 结构

**状态**：已采用（`kb-knowledge-graph` 模块已随迁移 031 标准 RAG 退役而删除）

## 背景

项目需要清晰分离认证、用户管理、文档处理、搜索/AI 等关注点。

## 决策

采用多模块 Maven，模块间单向依赖：
```
kb-common → kb-auth → kb-user → kb-document / kb-search → kb-app
```

> 当时还有 `kb-knowledge-graph` 模块承载标签树 / 自动打标 / 文档关系。随标准 RAG 竖井退役（迁移 031），该模块已整体删除。

## 后果

**优点**：
- 各模块职责清晰，可独立编译
- 强制单向依赖，防止循环引用

**缺点/注意**：
- 跨模块依赖需在 `kb-app/AppConfig.java` 手动装配
- 修改子模块后必须 `mvn install -pl <module> -am` 才能反映到 `kb-app`
