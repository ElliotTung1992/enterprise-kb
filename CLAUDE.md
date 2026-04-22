# 项目开发规范

## 环境说明

- 本项目使用 **JDK 21**，本机安装路径：`/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`
- 构建工具：Maven 3.9+
- 运行方式：`docker compose --env-file .env up -d`，应用端口 **8081**

## 项目结构

多模块 Maven 项目，模块依赖顺序：

```
kb-common → kb-auth → kb-user → kb-document / kb-search / kb-knowledge-graph → kb-app
```

| 模块 | 职责 |
|------|------|
| `kb-common` | 公共异常、DTO、工具类 |
| `kb-auth` | JWT 认证、登录注册、Token 刷新 |
| `kb-user` | 用户管理、知识空间、权限校验 |
| `kb-document` | 文档上传、解析、向量入库 |
| `kb-search` | 关键词检索、语义检索、混合检索、问答 |
| `kb-knowledge-graph` | 标签管理、知识图谱、自动打标 |
| `kb-app` | 启动入口、Bean 装配（AppConfig） |

## 分层规范

- **Controller** → **Service 接口** → **Service 实现** → **Mapper**
- Service 必须拆分为接口和实现类
- Service 实现类放在 `service/impl` 包下，例如：
  - 接口：`com.enterprise.kb.user.service.UserService`
  - 实现：`com.enterprise.kb.user.service.impl.UserServiceImpl`
- ORM 框架统一使用 **MyBatis**，SQL 写在 `resources/mapper/*.xml` 中，禁止字符串拼接 SQL

## 代码注释规范

- 所有 **Controller 类及其方法**必须添加 Javadoc 注释
- 所有 **Service 接口及其方法**必须添加 Javadoc 注释
- Javadoc 必须包含：功能描述、`@param` 参数说明、`@return` 返回值说明
- 注释语言统一使用**中文**
- 核心业务逻辑必须添加行内注释说明意图

## 安全规范

- 敏感配置（数据库密码、JWT 密钥、API Key）统一通过环境变量注入，禁止硬编码默认值
- JWT 密钥长度不得低于 32 字节（256 bits）
- 密码存储必须使用 BCrypt 加密
- 所有需要鉴权的接口使用 `@PreAuthorize` 注解声明权限

## 启动说明

1. 复制环境变量文件：`cp .env.example .env` 并填写各项配置
2. 启动所有服务：`docker compose --env-file .env up -d`
3. 应用健康检查：`curl http://127.0.0.1:8081/actuator/health`

本地开发（不使用 Docker 启动应用）需手动设置环境变量，并使用 JDK 21：

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
mvn -f kb-app/pom.xml spring-boot:run
```
