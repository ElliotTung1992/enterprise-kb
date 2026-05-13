# ADR-001: 多模块 Maven 结构

**状态**：已采用

## 背景

项目需要清晰分离认证、用户管理、文档处理、搜索/AI、知识图谱等关注点。

## 决策

采用多模块 Maven，模块间单向依赖：
```
kb-common → kb-auth → kb-user → kb-document / kb-search / kb-knowledge-graph → kb-app
```

## 后果

**优点**：
- 各模块职责清晰，可独立编译
- 强制单向依赖，防止循环引用

**缺点/注意**：
- 跨模块依赖需在 `kb-app/AppConfig.java` 手动装配
- 修改子模块后必须 `mvn install -pl <module> -am` 才能反映到 `kb-app`
