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

---

## 一、分层规范

**调用链路：** Controller → Service 接口 → Service 实现 → Mapper

- Service 必须拆分为接口和实现类，实现类放在 `service/impl` 包下
  - 接口：`com.enterprise.kb.user.service.UserService`
  - 实现：`com.enterprise.kb.user.service.impl.UserServiceImpl`
- Controller 只做参数校验和结果封装，不写业务逻辑
- Service 负责业务逻辑和事务管理，不直接操作 HTTP 对象（`HttpServletRequest` 等）
- Mapper 只做数据库 CRUD，不包含业务判断
- 禁止跨层调用（如 Controller 直接调 Mapper）

---

## 二、命名规范

### 类命名

| 类型 | 规则 | 示例 |
|------|------|------|
| 类/接口 | UpperCamelCase | `UserService`, `DocumentDto` |
| Controller | `XxxController` | `DocumentController` |
| Service 接口 | `XxxService` | `UserService` |
| Service 实现 | `XxxServiceImpl` | `UserServiceImpl` |
| Mapper | `XxxMapper` | `UserMapper` |
| DTO | `XxxDto` / `XxxRequest` / `XxxResponse` | `UserDto`, `LoginRequest` |
| 实体 | 与表名对应，单数 | `User`, `Document` |
| 常量类 | 全大写 + 下划线，类名 UpperCamelCase | `RoleType`, `DocumentStatus` |

### 方法命名

| 场景 | 前缀 | 示例 |
|------|------|------|
| 查询单条 | `get` / `find` | `getUserById`, `findByUsername` |
| 查询列表 | `list` / `findAll` | `listUsers`, `findAllActive` |
| 新增 | `create` / `add` / `insert` | `createUser` |
| 修改 | `update` | `updatePasswordHash` |
| 删除 | `delete` / `remove` | `deleteUser` |
| 存在判断 | `exists` / `is` | `existsByUsername` |

### 变量命名

- 局部变量和参数：lowerCamelCase
- 常量：`static final`，全大写下划线分隔，如 `MAX_FILE_SIZE`
- 布尔类型：以 `is` / `has` / `can` 开头，如 `isActive`
- 集合变量：使用复数，如 `users`、`documentIds`

---

## 三、代码注释规范

- 所有 **Controller 类及其方法**必须添加 Javadoc 注释
- 所有 **Service 接口及其方法**必须添加 Javadoc 注释
- Javadoc 必须包含：功能描述、`@param` 参数说明、`@return` 返回值说明
- 注释语言统一使用**中文**
- 核心业务逻辑、复杂算法必须添加行内注释说明意图
- 禁止无意义注释，如 `// 获取用户` 后紧跟 `getUser()`

---

## 四、异常处理规范

- 统一使用 `GlobalExceptionHandler` 处理异常，Controller 层不做 try-catch
- 业务异常抛 `KbException`（含 HTTP 状态码），资源不存在抛 `ResourceNotFoundException`
- 禁止 catch `Exception` 后静默忽略（`catch (Exception e) {}`）
- 禁止用异常做流程控制
- 对外接口统一返回 `ApiResponse<T>`，不直接暴露异常堆栈

```java
// 正确
throw new KbException("用户名已存在", HttpStatus.CONFLICT);
throw new ResourceNotFoundException("User", id);

// 错误
throw new RuntimeException("error");
```

---

## 五、日志规范

- 统一使用 `@Slf4j`（Lombok），禁止使用 `System.out.println`
- 日志级别使用原则：
  - `ERROR`：系统异常、需要立即介入的问题
  - `WARN`：业务异常、可预期的错误（如登录失败）
  - `INFO`：关键业务节点（服务启动、重要操作完成）
  - `DEBUG`：开发调试信息，生产环境关闭
- 日志中禁止打印密码、Token、完整身份证号等敏感信息
- 使用占位符，不使用字符串拼接：

```java
// 正确
log.info("用户 {} 登录成功", username);

// 错误
log.info("用户 " + username + " 登录成功");
```

---

## 六、数据库 / MyBatis 规范

- ORM 框架统一使用 **MyBatis**，SQL 写在 `resources/mapper/*.xml`
- 所有 SQL 参数必须使用 `#{param}` 占位符，**禁止使用 `${param}`**（防 SQL 注入）
- 表名、字段名使用 snake_case；Java 属性使用 camelCase，通过 `map-underscore-to-camel-case: true` 自动映射
- 软删除字段统一为 `deleted_at`，查询时必须加 `AND deleted_at IS NULL`
- 分页统一使用 PageHelper，禁止手动写 `LIMIT OFFSET`
- 复杂查询使用 `<if>` 动态 SQL，禁止在 Java 代码中拼接 SQL 字符串
- 主键统一使用 UUID，类型为 `OTHER`（PostgreSQL `uuid` 类型）：

```xml
WHERE id = #{id,jdbcType=OTHER}
```

---

## 七、RESTful API 规范

- URL 使用小写字母和连字符，资源名用复数名词：`/api/v1/documents`
- HTTP 方法语义：

| 方法 | 场景 | 示例 |
|------|------|------|
| `GET` | 查询 | `GET /api/v1/documents/{id}` |
| `POST` | 新增 | `POST /api/v1/documents` |
| `PUT` | 全量更新 | `PUT /api/v1/users/{id}` |
| `PATCH` | 部分更新 | `PATCH /api/v1/users/{id}/status` |
| `DELETE` | 删除 | `DELETE /api/v1/documents/{id}` |

- HTTP 状态码：200 成功，201 新建，400 参数错误，401 未认证，403 无权限，404 不存在，409 冲突，500 服务端错误
- 响应统一封装为 `ApiResponse<T>`，包含 `success`、`data`、`message`、`timestamp`
- 入参使用 `@Valid` + Bean Validation 注解校验，不在 Service 层重复校验格式

---

## 八、事务规范

- 事务注解加在 Service 实现类方法上，不加在接口上
- 只读操作加 `@Transactional(readOnly = true)` 提升性能
- 事务方法内禁止进行远程调用（HTTP、MQ）或耗时 IO（文件写入），防止长事务
- 异步任务（如文档向量化）在事务提交后触发，使用 `@TransactionalEventListener` 或在事务外启动

```java
// 正确
@Transactional(readOnly = true)
public UserDto getUserById(UUID id) { ... }

@Transactional
public void deleteUser(UUID id) { ... }
```

---

## 九、安全规范

- 敏感配置（数据库密码、JWT 密钥、API Key）统一通过环境变量注入，禁止硬编码默认值
- JWT 密钥长度不得低于 32 字节（256 bits），启动时校验
- 密码存储必须使用 BCrypt 加密，禁止 MD5/SHA1
- 所有需要鉴权的接口使用 `@PreAuthorize` 注解，权限粒度到知识空间级别
- 文件上传必须校验 MIME 类型白名单，禁止直接信任文件扩展名

---

## 十、代码质量规范

### 通用原则

- 方法长度不超过 80 行，超过则拆分
- 单个类不超过 500 行
- 方法参数不超过 5 个，超过则封装为对象
- 禁止魔法数字，使用常量或枚举替代

### 集合使用

- 返回空集合用 `Collections.emptyList()`，不返回 `null`
- 判断集合非空用 `!CollectionUtils.isEmpty(list)`，不用 `list != null && list.size() > 0`

### Optional 使用

- Mapper 返回可能为空的单条记录用 `Optional<T>`
- 业务层从 Optional 取值时，不存在则抛具体业务异常：

```java
User user = userMapper.findById(id)
    .orElseThrow(() -> new ResourceNotFoundException("User", id));
```

### Java 21 特性

- 优先使用 Record 定义不可变 DTO：`public record LoginRequest(String username, String password) {}`
- 使用 `switch` 表达式替代多分支 `if-else`
- 使用文本块（Text Block）处理多行 SQL 或 JSON 字符串

---

## 十一、Git 提交规范

提交信息格式：`<type>: <subject>`

| type | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 重构（不影响功能） |
| `docs` | 文档变更 |
| `test` | 测试相关 |
| `chore` | 构建/依赖/配置变更 |

示例：
```
feat: 新增文档批量删除接口
fix: 修复 JWT 过期后 userId 获取失败的问题
refactor: 将 ServiceImpl 移至 service/impl 子包
```

---

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
