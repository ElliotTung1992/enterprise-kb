# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Build & Run Commands

```bash
# Full build (all modules, skip tests)
mvn install -DskipTests

# Build a single module and its upstream dependencies
mvn install -pl kb-search -am -DskipTests

# Run the application locally (after building)
set -a && source .env && set +a
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn spring-boot:run -pl kb-app

# Start all infrastructure + app via Docker
docker compose --env-file .env up -d

# Health check
curl http://127.0.0.1:8081/actuator/health
```

> **Critical**: `mvn spring-boot:run -pl kb-app` loads sub-module JARs from `~/.m2`. Always run `mvn install -pl <module> -am -DskipTests` after changing any sub-module before starting the app.

There are currently **no automated tests** in this project.

---

## Architecture Overview

### Infrastructure Stack

| Component | Purpose | Port |
|-----------|---------|------|
| PostgreSQL 16 | Relational data (users, spaces, Markdown documents, QA sessions) | 5432 |
| Milvus 2.4 | Vector store for Markdown child-chunk embeddings (collection: `md_kb_chunks`, 1536-dim COSINE) | 19530 |
| Redis 7 | QA session chat history cache (TTL 24h) | 6379 |
| MinIO | Milvus object storage backend | 9000 |

> **标准（非 Markdown）RAG 竖井已全退役**（迁移 031）。原 `DocumentController` / `DocumentIngestionPipeline` / `HybridSearch` / `Semantic`+`KeywordSearch` / `QnAService` / `AgenticQnAService`、`kb_chunks` 集合、`documents`/`document_chunks` 等 6 张表、以及 `kb-knowledge-graph` 模块均已删除。系统现仅保留 **Markdown 结构感知 RAG 竖井**（设计见 `docs/design-md-structure-rag.md`）。

### Document Ingestion Pipeline (Markdown)

Upload → `MdDocumentController` → `MdDocumentService` → async `MdDocumentIngestionWorker` (virtual thread via `ingestionExecutor`):

1. **STRUCTURE SPLIT** — `MarkdownStructureIngestionService`: 按 H1-H3 切 parent（整段 section），parent 内再切段落级 child；表格双表示（检索用逐行线性化、返回用原始 markdown）；图片走 `MdImageUnderstandingService`（Noop / DashScope 可切换）
2. **PERSIST CHUNKS** — `md_parent_chunk` / `md_child_chunk` / `md_document_asset`（PostgreSQL）
3. **EMBED + VECTOR STORE** — `MdVectorStoreService`: child 的 `embed_text` 经 `EmbeddingModel` 入 Milvus `md_kb_chunks`

Document status transitions: `PENDING` → `PROCESSING` → `READY` / `FAILED`。原始 `.md` 存 MinIO（`DocumentObjectStorageService`）。

### Search & QA Pipeline (Markdown)

**MD Hybrid Search** (`MdHybridSearchService`): `MdVectorSearchService`（`md_kb_chunks` 向量召回）+ `MdKeywordSearchService`（`md_child_chunk` 上的关键词/BM25）并行，RRF 融合（child 粒度，融合阶段不去重）。

- Optional reranking: `RerankService` (DashScope `gte-rerank`)，在 child 粒度精排
- Optional HyDE / 改写: `HydeService` / `QueryRewriteService`
- **Parent Expansion** (`MdParentExpansionService`): topK child 按 `parentId` 折叠去重，回查整段 parent 正文 + 多窗节选后交给 LLM（small-to-big）

**MD QA** (`MdQnAServiceImpl`): MD hybrid search → rerank → parent expansion → prompt → LLM → persist session（`qa_sessions`/`qa_messages`，与会话端点共享）。

**MD Agentic QA** (`MdAgenticQnAServiceImpl`): Spring AI Alibaba `ReactAgent`，双工具 `searchKnowledgeBase`（搜 child）+ `readFullSection`（按需展开 parent）。Token budget 由 `AgenticTokenBudgetService` 管理。

**Streaming**: `MdQnAController` 暴露 SSE 端点（`text/event-stream`）。

> 此外，`kb-search` 还含**商城客服助手 / 投诉工作流**（`CustomerAssistantService` / `Complaint*` / `DomainHandler`），它们用各自的 `ReactAgent`，**不依赖**知识库检索，与 MD 竖井相互独立。

### AI Provider System

`ModelProviderResolver` injects named beans (`@Qualifier`) for each provider and switches at runtime based on `modelProvider` in the request:

- **Chat**: `dashscopeChatClient`, `minimaxChatClient`, `anthropicChatClient`
- **Embedding**: `dashscopeEmbeddingModel` (marked `@Primary` via `BeanFactoryPostProcessor`), `minimaxEmbeddingModel`
- Default chat provider: `MINIMAX`; default embedding: `DASHSCOPE`

> **Embedding dimension warning**: All providers use 1536-dim vectors. Switching embedding providers requires rebuilding the `md_kb_chunks` collection and re-ingesting all documents.

### Online LLM Tracing (LangFuse via OTLP) — ADR-015

在线分布式 LLM tracing：**Micrometer Observation → OpenTelemetry SDK → OTLP/HTTP → 自部署 LangFuse**。「一次请求 = 一棵 span 树」。设计见 `docs/design-langfuse-tracing.md`，决策见 `wiki/decisions/adr-015-langfuse-tracing.md`。

- **总开关**：`enterprise.kb.tracing.enabled`（环境变量 `KB_TRACING_ENABLED`，默认 **false**）。关闭时 `management.tracing.enabled=false` → Observation 不产 span，零开销；自定义 tracing bean（reactor 传播 hook / 脱敏 filter / 业务属性 SpanProcessor）均经 `@ConditionalOnProperty` 不装配。导出器仅当 `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` 就绪时才装配（Spring Boot OTLP 自动配置）。
- **依赖**：`kb-app` 引 `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`（版本由 Spring Boot BOM 管理）。
- **共享 tracing 工具**（`kb-common` 的 `com.enterprise.kb.common.tracing`）：`TracingSupport`（根/子 span 命令式助手，开关关闭时直接跑业务体）、`TracingContextHolder`（业务属性 thread-local + 注册到 context-propagation，供跨线程传播）、`TracingAttributes`（LangFuse 属性 key）、`SensitiveDataRedactor`（MVP 正则脱敏 + 截断）。
- **span 模型**：根 span（service 入口自建：`kb.qa.ask` / `kb.qa.ask.agentic` / `kb.qa.ask.stream` / `kb.ingest.document`）→ LLM span（Spring AI 自动 `gen_ai.*`）+ tool span（`TracingToolInterceptor`，`ReactAgent.builder().interceptors(...)`）+ graph/node 子树（`CompileConfig.observationRegistry`）+ 检索四段 span（`kb.retrieval.vector/keyword/rerank/parent_expansion`）。
- **埋点盲区已补**：minimax `OpenAiChatModel`/`ChatClient`（`AiModelConfig`）与 `mdVectorStore`（`AppConfig`）均注入 `ObservationRegistry`。
- **跨线程传播**：① `TracingConfig` 在 enabled 时 `Hooks.enableAutomaticContextPropagation()`；② `MdHybridSearchServiceImpl` 用 `ContextSnapshot.wrapExecutor` 包并行检索的 executor；③ `spring.ai.alibaba.tool.async.enabled=false` 关异步 tool manager。
- **业务属性下传**：根 span 把 `langfuse.user.id`/`langfuse.session.id`/metadata 写入 `TracingContextHolder`，`LangfuseChildAttributeSpanProcessor`（OTel `SpanProcessor` bean）在每个子 span `onStart` 复制上去。走本地 thread-local，**不**经 baggage，不外发给模型 provider。
- **脱敏 / 截断（D5）**：正文进 trace 前经 `SensitiveDataObservationFilter`（高基数 tag 兜底）+ 源头按 `enterprise.kb.tracing.max-*-chars` 截断。
- **基础设施**：`docker compose --profile tracing up -d` 起 `clickhouse` + `langfuse-web`（宿主 3001，attu 占用 3000）+ `langfuse-worker`；复用现有 PG（独立库 `langfuse`，由 `postgres-init.sql` 建）/ Redis（独立 db index）/ MinIO（独立 bucket `langfuse`，需预先创建）。LangFuse 必需密钥（`LANGFUSE_SALT` / `LANGFUSE_ENCRYPTION_KEY` / `LANGFUSE_NEXTAUTH_SECRET` / `CLICKHOUSE_PASSWORD`）见 `.env.example`，禁止用示例默认值。

> **状态**：代码已落地、全模块编译通过；运行态（LangFuse span 树渲染、prompt/completion event→attribute 映射、跨线程传播实测）仍属 ADR-015 §11「待验证」，需起栈联调。

### Permission Model

Space-level RBAC enforced via `@PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")`. `SpacePermissionEvaluator` resolves the authenticated user's `RoleType` (OWNER/EDITOR/VIEWER) against `user_space_roles` table. Wired in `AppConfig` to avoid circular cross-module dependencies.

### Cross-Module Dependency Resolution

`AppConfig` (in `kb-app`) manually constructs beans that would otherwise create circular cross-module dependencies:
- `AuthService`/`AuthServiceImpl` — needs `UserService` from `kb-user` inside `kb-auth`
- `MethodSecurityExpressionHandler` — registers `SpacePermissionEvaluator`
- `ingestionExecutor` — JDK 21 virtual thread executor for document ingestion
- `milvusClient` — hand-defined `MilvusServiceClient` for `mdVectorStore`. Spring AI's `MilvusVectorStoreAutoConfiguration` is excluded via `spring.autoconfigure.exclude`, so no `kb_chunks` collection is auto-created (standard RAG decommissioned)

### Database Migrations

Liquibase manages schema via `kb-app/src/main/resources/db/changelog/db.changelog-master.xml`. Migration files are numbered `001–031` in `db/changelog/`. New migrations must be added there as numbered SQL files and referenced in the master changelog. (`031-drop-standard-rag.sql` dropped the 6 standard-RAG tables.)

MyBatis mapper XMLs live in each module's `src/main/resources/mapper/`. The global `type-handlers-package` for UUID/JSONB handlers is `com.enterprise.kb.document.typehandler`.

---

# 项目开发规范

## 文档目录

项目文档位于 `docs/` 目录：

| 文件 | 内容 |
|------|------|
| `docs/operation.md` | 用户操作手册：功能说明、API 接口速查、部署运维 |
| `docs/er-diagram.puml` | 数据库 ER 图（PlantUML 格式，基础用户/空间/会话体系；标准 RAG 表已退役不再绘制） |
| `docs/design-md-structure-rag.md` | Markdown 结构感知 RAG（small-to-big 父子索引）设计文档 |
| `planning/plan.md` | 可扩展内容与优化点规划（技术向：RAG/性能/质量） |
| `planning/plan2.md` | 可扩展内容与优化点规划（产品向：体验/安全/集成） |

---

## 环境说明

- 本项目使用 **JDK 21**，本机安装路径：`/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`
- 构建工具：Maven 3.9+
- 运行方式：`docker compose --env-file .env up -d`，应用端口 **8081**

## 项目结构

多模块 Maven 项目，模块依赖顺序：

```
kb-common → kb-auth → kb-user → kb-document / kb-search → kb-app
```

| 模块 | 职责 |
|------|------|
| `kb-common` | 公共异常、DTO、工具类 |
| `kb-auth` | JWT 认证、登录注册、Token 刷新 |
| `kb-user` | 用户管理、知识空间、权限校验 |
| `kb-document` | Markdown 文档上传、结构化解析、图片理解、向量入库 |
| `kb-search` | Markdown 混合检索、问答（标准/Agentic）、客服助手、投诉工作流、Eval、**会话管理** |
| `kb-app` | 启动入口、Bean 装配（AppConfig） |

> `kb-knowledge-graph` 模块已随标准 RAG 退役删除。

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
- 配置类需要写详细的注释

### 实体类规范

所有 Model 类（位于 `{module}/model/` 目录下）必须遵循以下规范：

1. **类注解**：`@Getter @Setter`（Lombok），如需表名关联可加 `@Table(name = "table_name")`
2. **字段注释**：每个字段必须写中文注释，说明其含义和用途
3. **默认值**：`= Instant.now()` 等默认值写在字段声明上
4. **非列字段**标记：非数据库列字段（如 `messageCount` 由 SQL COUNT 子查询填充）加 `/** 非列字段 */` 注释
5. **布尔字段**：统一不加 `is` 前缀，直接用动词或形容词命名，如 `active`、`autoDetected`
6. **禁止 JPA 完整注解**：不使用 `@Entity`、`@Column`、`@Id` 等 JPA 注解（项目用 MyBatis），`@Table` 可选使用
7. **枚举字段**：使用 `RoleType`、`DocumentStatus` 等常量类，不直接映射数据库 VARCHAR 值

```java
@Getter
@Setter
public class QaChatSession {

    private UUID id;
    /** 所属知识空间 ID */
    private UUID spaceId;
    /** 会话所有者用户 ID */
    private UUID userId;
    /** 会话标题（默认取首条问题前 50 字） */
    private String title;
    /** 创建时间 */
    private Instant createdAt;
    /** 最后活跃时间 */
    private Instant updatedAt;
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;
    /** 非列字段，由 SQL COUNT 子查询填充 */
    private int messageCount;
}
```

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

## 十一、问答会话管理

### 数据模型

会话元数据持久化到 PostgreSQL，对话上下文同时缓存到 Redis（TTL 24 小时）。

**`qa_sessions` 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 由后端在首次问答时生成，前端传 `sessionId` 延续 |
| `space_id` | UUID | 所属知识空间 |
| `user_id` | UUID | 会话所有者 |
| `title` | VARCHAR(200) | 默认取首条问题（截断至 50 字符） |
| `created_at` / `updated_at` | TIMESTAMPTZ | 创建 / 最后活跃时间 |
| `deleted_at` | TIMESTAMPTZ | 软删除标记 |

**`qa_messages` 表**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID PK | 自动生成 |
| `session_id` | UUID FK | 关联 qa_sessions（ON DELETE CASCADE） |
| `role` | VARCHAR(20) | `user` 或 `assistant` |
| `content` | TEXT | 消息正文 |
| `created_at` | TIMESTAMPTZ | 消息时间 |

### 会话 API

所有接口均需空间级 `VIEWER` 权限（`@PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")`）。

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/spaces/{spaceId}/qa/sessions` | 当前用户的会话列表（含消息数，按活跃时间倒序） |
| `GET` | `/api/v1/spaces/{spaceId}/qa/sessions/{sessionId}/messages` | 指定会话的历史消息（仅所有者可访问） |
| `PATCH` | `/api/v1/spaces/{spaceId}/qa/sessions/{sessionId}/title` | 修改会话标题，请求体 `{"title":"..."}` |
| `DELETE` | `/api/v1/spaces/{spaceId}/qa/sessions/{sessionId}` | 软删除会话并清空对应 Redis 历史 |

### 会话持久化机制

每次问答（`/qa/ask` 或 `/qa/ask/advanced`）成功后，`QnAServiceImpl` / `AgenticQnAServiceImpl` 会调用 `QaChatSessionService.saveExchange()`：

1. 首次调用时自动创建会话记录（title = 首条问题）
2. 后续调用仅追加消息并刷新 `updated_at`
3. 持久化失败以 WARN 日志记录，**不影响正常问答响应**

### 前端交互

- 空间切换时调用 `GET /sessions` 刷新侧边栏列表
- 点击历史会话时调用 `GET /sessions/{id}/messages` 恢复对话记录
- 删除会话调用 `DELETE /sessions/{id}` 同步清理后端和 Redis
- 会话 `sessionId` 在问答响应中返回，前端持有后传给下一次请求实现多轮对话

---

## 十二、AI 多模型配置

### 多 EmbeddingModel 冲突处理

系统同时启用 DashScope 和 MiniMax，两个自动配置各自注册了 `EmbeddingModel` bean，加上 `AiModelConfig` 手工声明的 `minimaxEmbeddingModel`，共 3 个候选，导致 MilvusVectorStore 自动装配歧义。

**解决方案**（`AiModelConfig.embeddingModelPrimaryPostProcessor`）：使用 `BeanFactoryPostProcessor` 在 bean 实例化前将 `dashscopeEmbeddingModel` 的 bean 定义设为 `primary = true`，Milvus 即可唯一选中它。其他 EmbeddingModel bean 仍可通过 `@Qualifier` 按名称注入，供 `ModelProviderResolver` 使用。

> **注意**：若新增 AI 提供商并注册新的 `EmbeddingModel` bean，需在该 PostProcessor 中确认只有一个 bean 被标为 primary，否则启动报歧义错误。

### ModelProviderResolver

`ModelProviderResolver` 通过 `@Qualifier` 按名称注入各提供商的 `ChatClient` 和 `EmbeddingModel`，在运行时根据请求参数 `modelProvider` 动态切换：

| 配置项 | 默认值 |
|--------|--------|
| `enterprise.kb.ai.default-provider` | `DASHSCOPE` |
| `enterprise.kb.ai.default-embedding-provider` | `DASHSCOPE` |

---

## 十三、Git 提交规范

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
# 修改了任意子模块代码后，必须先 install 更新本地仓库，再启动 kb-app
mvn install -pl kb-search -am -DskipTests   # 以 kb-search 为例
set -a && source .env && set +a
mvn spring-boot:run -pl kb-app
```

> **注意**：`mvn spring-boot:run -pl kb-app` 从 `~/.m2` 加载子模块 JAR。若直接运行而未先 `install`，改动不会生效。
