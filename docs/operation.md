# 企业知识库 — 功能操作文档

> **重要变更（标准 RAG 退役）**：标准（非 Markdown）RAG 竖井已全部下线。文档上传与问答仅支持
> **Markdown 结构感知 RAG**，接口前缀由 `/documents`、`/qa`、`/search` 迁移为 `/md-documents`、
> `/md-qa`（会话管理仍为两竖井共享的 `/qa/sessions`）。原“搜索”“标签与知识图谱”功能及其接口已移除。
> 设计见 `docs/md-structure-rag.md`。

## 目录

1. [快速开始](#1-快速开始)
2. [账号与认证](#2-账号与认证)
3. [空间管理](#3-空间管理)
4. [Markdown 文档管理](#4-markdown-文档管理)
5. [搜索（已下线）](#5-搜索已下线)
6. [AI 问答（Markdown）](#6-ai-问答markdown)
7. [标签与知识图谱（已下线）](#7-标签与知识图谱已下线)
8. [系统管理（管理员）](#8-系统管理管理员)
9. [API 参考](#9-api-参考)
10. [部署与运维](#10-部署与运维)
11. [Langfuse Tracing 启动调试](langfuse-tracing.md#第四部分--操作手册启动--调试)

---

## 1. 快速开始

### 系统架构

```
浏览器
  │
  └─► Spring Boot 应用 (8081)
          │
          ├─► PostgreSQL 5432   — 用户/空间/Markdown 文档元数据、会话记录
          ├─► Redis 6379        — 会话上下文（LLM 多轮记忆，24h TTL）
          ├─► Milvus 19530      — 向量索引（md_kb_chunks，Markdown child 向量）
          ├─► MinIO 9000        — Milvus 对象存储
          └─► etcd 2379         — Milvus 元数据
```

### 启动服务

```bash
# 1. 复制环境变量文件并填写配置
cp .env.example .env

# 2. 启动所有服务（首次约需 1-2 分钟）
docker compose --env-file .env up -d

# 3. 查看服务状态
docker compose ps

# 4. 查看应用日志
docker compose logs -f app

# 5. 构建镜像文件
docker compose build app
```

### 访问地址

| 服务 | 地址 | 说明 |
|------|------|------|
| 知识库应用 | http://localhost:8081 | 主应用 |
| MinIO 控制台 | http://localhost:9001 | 对象存储管理 |
| PostgreSQL | localhost:5432 | 数据库（需客户端工具） |

### 必填环境变量（.env）

```bash
PG_PASSWORD=your_strong_password          # 数据库密码（必填）
JWT_SECRET=your_random_256bit_hex         # JWT 密钥，至少 32 字符（必填）
DASHSCOPE_API_KEY=your_dashscope_key      # 默认 embedding / rerank 提供商——通义千问（必填）

# 默认 chat 提供商——本地 llama.cpp（OpenAI 兼容接口）
LLAMA_CPP_BASE_URL=http://localhost:8079
LLAMA_CPP_MODEL=qwen3-vl-8b-instruct
LLAMA_CPP_API_KEY=local

# 可选 AI 提供商
OPENAI_API_KEY=
ANTHROPIC_API_KEY=
```

生成安全的 JWT 密钥：

```bash
openssl rand -hex 32
```

---

## 2. 账号与认证

### 2.1 注册账号

**接口**：`POST /api/v1/auth/register`

**操作步骤**：

1. 访问 http://localhost:8081/login.html
2. 点击"注册"（或直接调用 API）
3. 填写用户名、邮箱、密码、姓名

**请求示例**：

```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "zhangsan",
    "email": "zhangsan@company.com",
    "password": "SecurePass123",
    "fullName": "张三"
  }'  
  
-- windows版本  
Invoke-RestMethod -Method POST -Uri "http://localhost:8081/api/v1/auth/register" `
-ContentType "application/json" `
-Body '{"username": "zhangsan1","email": "zhangsan1@company.com","password": "SecurePass123","fullName": "张三1"}'
```

**返回**：

```json
{
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "eyJ...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "userId": "uuid",
    "username": "zhangsan"
  }
}
```

### 2.2 登录

**接口**：`POST /api/v1/auth/login`

**操作步骤**：

1. 访问 http://localhost:8081/login.html
2. 输入用户名和密码
3. 登录成功后自动跳转到仪表盘

**请求示例**：

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "zhangsan", "password": "SecurePass123"}'
```

**Token 有效期**：

- Access Token：60 分钟
- Refresh Token：30 天

### 2.3 刷新 Token

当 Access Token 过期时，使用 Refresh Token 续期，无需重新登录：

```bash
curl -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "eyJ..."}'
```

### 2.4 退出登录

```bash
curl -X POST http://localhost:8081/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "eyJ..."}'
```

前端点击"退出登录"后，会自动清除 localStorage 中的 Token 并跳转至登录页。

### 2.5 修改密码

```bash
curl -X PUT http://localhost:8081/api/v1/auth/me/password \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"oldPassword": "旧密码", "newPassword": "新密码"}'
```

### 2.6 查看当前用户信息

```bash
curl http://localhost:8081/api/v1/auth/me \
  -H "Authorization: Bearer {accessToken}"
```

---

## 3. 空间管理

**空间**是知识库的基本隔离单元。每个空间有独立的 Markdown 文档和成员权限。

### 空间角色说明

| 角色 | 权限 |
|------|------|
| VIEWER | 查看文档、AI 问答 |
| EDITOR | 上传/删除 Markdown 文档 |
| ADMIN | 管理成员权限、删除空间 |

### 3.1 创建空间

**页面操作**：在仪表盘顶部空间下拉框右侧点击 **+** 按钮，填写空间名称和描述后点击"创建"。

**接口**：`POST /api/v1/spaces`

```bash
curl -X POST http://localhost:8081/api/v1/spaces \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"name": "技术文档", "description": "研发团队技术规范与设计文档"}'
```

### 3.2 查看我的空间列表

**页面操作**：仪表盘顶部的空间下拉框展示所有可访问空间。

**接口**：`GET /api/v1/spaces`

```bash
curl http://localhost:8081/api/v1/spaces \
  -H "Authorization: Bearer {accessToken}"
```

### 3.3 添加空间成员

**接口**：`POST /api/v1/spaces/{spaceId}/members`

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/members \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"userId": "用户UUID", "role": "EDITOR"}'
```

### 3.4 修改成员角色

**接口**：`PUT /api/v1/spaces/{spaceId}/members/{userId}`

```bash
curl -X PUT http://localhost:8081/api/v1/spaces/{spaceId}/members/{userId} \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"role": "ADMIN"}'
```

### 3.5 移除成员

**接口**：`DELETE /api/v1/spaces/{spaceId}/members/{userId}`

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId}/members/{userId} \
  -H "Authorization: Bearer {accessToken}"
```

### 3.6 删除空间

> 需要 ADMIN 角色。删除后空间内所有 Markdown 文档及向量数据将一并删除，操作不可逆。

**接口**：`DELETE /api/v1/spaces/{spaceId}`

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId} \
  -H "Authorization: Bearer {accessToken}"
```

---

## 4. Markdown 文档管理

> 系统仅支持 **Markdown（`.md`）文档**。接口前缀为 `/api/v1/spaces/{spaceId}/md-documents`。
> 入库按文档结构（H1-H3）做 small-to-big 父子切分（设计见 `docs/md-structure-rag.md`）。

### 4.1 上传文档

**页面操作**：

1. 访问 http://localhost:8081/md-rag.html
2. 在顶部选择目标空间
3. 选择 `.md` 文件，点击"上传并入库"

**支持格式**：仅 Markdown（`text/markdown`，`.md`）。单文件最大 100 MB。

**入库处理**：

- 先按 H1-H3 标题把文档切成 parent（每个 parent 是一整段 section），再在 parent 内部切出段落级的 child。这就是 small-to-big 的思路：用小而精的 child 做召回，一旦命中就把整段 parent 返回给 LLM。
- 表格采用双表示：检索时用逐行自然语言化后的文本，返回给 LLM 时则换回原始的 markdown 表格。
- Markdown 中引用的图片会交给图片理解服务处理（`MdImageUnderstandingService`，默认是占位实现，可切换成 DashScope）；生成的 caption 作为 `md_document_asset` 异步补充进来，不阻塞正文问答。
- 原始 `.md` 文件存入 MinIO。

**上传接口**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/md-documents/upload \
  -H "Authorization: Bearer {accessToken}" \
  -F "file=@/path/to/document.md"
```

上传后文档异步入库，初始状态为 `PENDING`。

### 4.2 文档处理状态

```
PENDING（已接收）→ PROCESSING（结构切分 / child 向量化）→ READY（可问答） / FAILED（处理失败）
```

入库流程大致是：先做结构切分（切出 parent / child、做表格线性化、跑图片理解），然后把 child 写入
`md_kb_chunks` 向量库，同时把 `md_parent_chunk` / `md_child_chunk` / `md_document_asset` 等元数据落库。

### 4.3 查看文档列表

```bash
curl "http://localhost:8081/api/v1/spaces/{spaceId}/md-documents?page=0&size=20" \
  -H "Authorization: Bearer {accessToken}"
```

**返回字段说明**：

| 字段 | 说明 |
|------|------|
| id | 文档 UUID |
| title | 文档标题 |
| mimeType | 恒为 `text/markdown` |
| fileSize | 文件大小（字节） |
| status | PENDING / PROCESSING / READY / FAILED |
| chunkCount | child 分块数量 |
| errorMessage | 处理失败时的错误信息 |
| createdAt | 上传时间 |

### 4.4 查看文档详情

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/md-documents/{documentId} \
  -H "Authorization: Bearer {accessToken}"
```

### 4.5 删除文档

> 需要 EDITOR 角色。删除后同步清除 `md_parent_chunk` / `md_child_chunk` / `md_document_asset`
> 记录与 `md_kb_chunks` 向量，操作不可逆。

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId}/md-documents/{documentId} \
  -H "Authorization: Bearer {accessToken}"
```

---

## 5. 搜索（已下线）

> 标准搜索接口（`POST /search`、`/search/keyword`、`/search/hybrid`）已随标准 RAG 竖井一并下线。
>
> Markdown 竖井不再单独暴露检索端点——检索能力已经整合进问答流程：先由 `MdHybridSearchService` 做混合召回
> （`md_kb_chunks` 向量召回 + `md_child_chunk` 关键词 / BM25，再用 RRF 融合），接着 rerank，最后做 parent
> 展开，整个过程都在 [AI 问答（Markdown）](#6-ai-问答markdown) 内部完成。如果想看清检索究竟命中了哪些内容，
> 调用问答接口、查看返回里的 `citations` 字段即可。

---

## 6. AI 问答（Markdown）

### 6.1 功能说明

AI 问答构建在 Markdown 结构感知 RAG 之上，整体分四步：先用混合检索（向量 + 关键词，RRF 融合）召回相关的
child 片段；再经 rerank 精排；然后按 parent 把命中的 child 折叠、展开成整段 section（即 small-to-big）；
最后由 LLM 结合这些上下文生成回答。回答会附带来源引用（文档标题 + 章节面包屑），便于溯源和核对。接口前缀为
`/api/v1/spaces/{spaceId}/md-qa`。

### 6.2 页面操作

1. 访问 http://localhost:8081/md-rag.html
2. 在顶部选择目标空间（切换空间会自动开始新对话）
3. 在输入框输入问题，按 Enter 发送（Shift+Enter 换行）
4. 查看"引用来源"了解回答依据
5. 左侧会话列表展示当前空间的历史对话，点击可切换查看

### 6.3 支持的 AI 模型

| 提供商 | 特点 | 需要配置 |
|--------|------|----------|
| llama.cpp（默认 chat） | 本地部署，OpenAI 兼容接口，零调用成本 | LLAMA_CPP_BASE_URL / LLAMA_CPP_MODEL |
| DashScope | 通义千问；同时提供 embedding（默认）与 rerank | DASHSCOPE_API_KEY |
| Anthropic | Claude，长文本理解出色 | ANTHROPIC_API_KEY |

> 通过请求体 `modelProvider`（`LLAMA_CPP` / `DASHSCOPE`）切换 chat 模型；不传则用默认 `LLAMA_CPP`。embedding 固定用 DashScope。

### 6.4 问答接口（阻塞式）

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/md-qa/ask \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "我们的数据库连接池默认最大连接数是多少？",
    "modelProvider": "LLAMA_CPP",
    "topK": 5
  }'
```

**返回示例**：

```json
{
  "data": {
    "answer": "根据配置文档，数据库连接池（HikariCP）的默认最大连接数为 **20**，最小空闲连接数为 **5**，连接超时时间为 **30 秒**。",
    "sessionId": "session-uuid",
    "citations": [
      {
        "documentId": "uuid",
        "documentTitle": "部署运维手册.md",
        "section": "部署运维 > 数据库 > 连接池",
        "excerpt": "maximum-pool-size: 20\nminimum-idle: 5\nconnection-timeout: 30000",
        "score": 0.94
      }
    ],
    "modelUsed": "LLAMA_CPP",
    "tokensUsed": 856
  }
}
```

引用以 parent section 为单位（文档标题 + 章节面包屑），整段超长时按命中位置多窗节选并标注"节选"。

### 6.5 连续对话（多轮问答）

将上一次返回的 `sessionId` 传入下一次请求，AI 会记住对话上下文：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/md-qa/ask \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "那么最小空闲连接数呢？",
    "sessionId": "上一次返回的 session-uuid",
    "modelProvider": "LLAMA_CPP",
    "topK": 5
  }'
```

### 6.6 Agentic 流式问答（多跳推理 + 逐步输出）

LLM 以 Agent 的身份自主决策，手里有两个工具：`searchKnowledgeBase`（搜 child 片段）和
`readFullSection`（按需展开某个 parent 的整段正文）。接口以 SSE JSON 事件流返回工具过程、思考片段、答案增量和引用：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/md-qa/ask/agentic/stream \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"question": "对比安装章节与升级章节对依赖版本的要求", "modelProvider": "LLAMA_CPP"}'
```

### 6.7 会话管理

每次问答都会自动创建或续写会话记录（标题取问题前 50 字），无需手动操作。会话统一存放在 `qa_sessions` 表中——这张表历史上由标准 RAG 与 Markdown RAG 两条竖井共用，因此会话管理的接口前缀沿用 `/qa/sessions`，而非 `/md-qa`。

**查看会话列表**：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/qa/sessions \
  -H "Authorization: Bearer {accessToken}"
```

**查看会话消息列表**：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/qa/sessions/{sessionId}/messages \
  -H "Authorization: Bearer {accessToken}"
```

**重命名会话**：

```bash
curl -X PATCH http://localhost:8081/api/v1/spaces/{spaceId}/qa/sessions/{sessionId}/title \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"title": "新标题"}'
```

**删除会话**：

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId}/qa/sessions/{sessionId} \
  -H "Authorization: Bearer {accessToken}"
```

> 删除会话同时清除 Redis 中的对话上下文，该会话的多轮历史不可恢复。

---

## 7. 标签与知识图谱（已下线）

> `kb-knowledge-graph` 模块已随标准 RAG 竖井一并退役：标签与知识图谱相关的接口
> （`/tags`、`/tags/tree`、`/tags/graph`，以及合并 / 删除标签等）连同 `tags` / `document_tags` 表都已全部移除。
> Markdown 竖井是按文档自身的结构（H1-H3 章节）来组织检索的，本期不提供打标、文档关系、图谱可视化这些能力。

---

## 8. 系统管理（管理员）

> 以下操作需要 `ROLE_SYSTEM_ADMIN` 角色。

### 8.1 查看所有用户

```bash
curl "http://localhost:8081/api/v1/users?page=0&size=20" \
  -H "Authorization: Bearer {adminToken}"

# 按关键词搜索
curl "http://localhost:8081/api/v1/users?keyword=张" \
  -H "Authorization: Bearer {adminToken}"
```

### 8.2 创建用户

```bash
curl -X POST http://localhost:8081/api/v1/users \
  -H "Authorization: Bearer {adminToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "lisi",
    "email": "lisi@company.com",
    "password": "InitPassword123",
    "fullName": "李四"
  }'
```

### 8.3 删除用户

```bash
curl -X DELETE http://localhost:8081/api/v1/users/{userId} \
  -H "Authorization: Bearer {adminToken}"
```

### 8.4 系统健康检查

```bash
# 服务健康状态
curl http://localhost:8081/actuator/health

# 指标数据
curl http://localhost:8081/actuator/metrics \
  -H "Authorization: Bearer {adminToken}"
```

---

## 9. API 参考

### 9.1 统一响应格式

所有接口均返回以下结构：

```json
{
  "success": true,
  "message": "success",
  "data": { },
  "timestamp": "2026-04-21T10:30:00Z"
}
```

**错误响应**：

```json
{
  "success": false,
  "message": "具体错误描述",
  "data": null,
  "timestamp": "2026-04-21T10:30:00Z"
}
```

### 9.2 通用 HTTP 状态码

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 201 | 创建成功 |
| 202 | 已接收，后台处理中（上传/重处理） |
| 400 | 请求参数错误 |
| 401 | 未认证（Token 缺失或过期） |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务内部错误 |

### 9.3 接口速查表

#### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/auth/login | 登录 |
| POST | /api/v1/auth/register | 注册 |
| POST | /api/v1/auth/refresh | 刷新 Token |
| POST | /api/v1/auth/logout | 退出登录 |
| GET | /api/v1/auth/me | 获取当前用户信息 |
| PUT | /api/v1/auth/me/password | 修改密码 |

#### 空间

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/spaces | 我的空间列表 |
| POST | /api/v1/spaces | 创建空间 |
| GET | /api/v1/spaces/{id} | 空间详情 |
| DELETE | /api/v1/spaces/{id} | 删除空间 |
| GET | /api/v1/spaces/{id}/members | 成员列表 |
| POST | /api/v1/spaces/{id}/members | 添加成员 |
| PUT | /api/v1/spaces/{id}/members/{uid} | 修改成员角色 |
| DELETE | /api/v1/spaces/{id}/members/{uid} | 移除成员 |

#### Markdown 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/spaces/{sid}/md-documents | 文档列表（分页） |
| POST | /api/v1/spaces/{sid}/md-documents/upload | 上传 .md 文件 |
| GET | /api/v1/spaces/{sid}/md-documents/{did} | 文档详情 |
| DELETE | /api/v1/spaces/{sid}/md-documents/{did} | 删除文档 |

#### AI 问答（Markdown）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/spaces/{sid}/md-qa/ask | 问答（阻塞式） |
| POST | /api/v1/spaces/{sid}/md-qa/ask/agentic/stream | Agentic 多跳问答（流式 SSE） |
| GET | /api/v1/spaces/{sid}/qa/sessions | 会话列表（两竖井共享） |
| GET | /api/v1/spaces/{sid}/qa/sessions/{sessionId}/messages | 会话消息列表 |
| PATCH | /api/v1/spaces/{sid}/qa/sessions/{sessionId}/title | 重命名会话 |
| DELETE | /api/v1/spaces/{sid}/qa/sessions/{sessionId} | 删除会话 |

> 标准 `/documents`、`/search`、`/tags` 系列接口及知识图谱接口已随标准 RAG 退役移除。

#### 用户管理（管理员）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/users | 用户列表 |
| POST | /api/v1/users | 创建用户 |
| GET | /api/v1/users/{uid} | 用户详情 |
| DELETE | /api/v1/users/{uid} | 删除用户 |

---

## 10. 部署与运维

### 10.1 Docker Compose 配置

**服务组成**：

| 服务 | 镜像 | 内部端口 | 外部端口 | 说明 |
|------|------|----------|----------|------|
| app | enterprise-kb-app | 8080 | 8081 | Spring Boot 应用 |
| postgres | postgres:16 | 5432 | 5432 | 关系型数据库 |
| redis | redis:7 | 6379 | — | 会话上下文缓存（LLM 多轮记忆） |
| milvus | milvusdb/milvus:v2.4.8 | 19530 | 19530 | 向量数据库 |
| etcd | quay.io/coreos/etcd:v3.5.14 | 2379 | — | Milvus 元数据存储 |
| minio | minio/minio | 9000/9001 | 9000/9001 | 对象存储 |

所有服务均配置健康检查，应用容器会等待 PostgreSQL 和 Milvus 就绪后再启动。

### 10.2 数据持久化

以下 Docker volume 持久化数据：

| Volume | 内容 |
|--------|------|
| postgres_data | 数据库数据 |
| milvus_data | 向量索引数据 |
| minio_data | MinIO 对象存储 |
| etcd_data | etcd 元数据 |
| uploads_data | 上传的原始文件 |

### 10.3 常用运维命令

```bash
# 查看服务状态
docker compose ps

# 查看应用日志（实时）
docker compose logs -f app

# 查看所有服务日志
docker compose logs -f

# 重启应用
docker compose restart app

# 重新构建并启动应用
docker compose build app && docker compose up -d app

# 停止所有服务
docker compose down

# 停止并删除所有数据（谨慎！不可恢复）
docker compose down -v

# 一步到位
docker compose build app        # 只重建 app 镜像                                          
docker compose up -d            # 启动所有服务（app 用新镜像，其他服务不重建）
mvn clean package -DskipTests && docker compose --env-file .env up -d --build app
```

### 10.4 数据库迁移

Liquibase 会在应用启动时自动执行迁移脚本，位于：

```
kb-app/src/main/resources/db/changelog/db.changelog-master.xml
```

如需手动检查数据库：

```bash
# 连接 PostgreSQL
docker exec -it kb-postgres psql -U kb_user -d enterprise_kb

# 查看 Liquibase 变更历史
SELECT * FROM databasechangelog ORDER BY dateexecuted DESC LIMIT 10;
```

### 10.5 向量维度配置

当前配置使用 1536 维向量（DashScope `text-embedding-v2`）。如需修改，需同步更新 `application.yml` 中的 `embedding-dimension` 并**重建** `md_kb_chunks` 集合（现有数据需重新向量化）。

### 10.6 文件存储

原始 `.md` 文件经 `DocumentObjectStorageService` 存入 MinIO（对象存储，挂载到 Docker volume `minio_data`），数据库中以 `md_documents.object_key` 引用。

### 10.7 Markdown 图片理解运维

Markdown 中引用的图片由 `MdImageUnderstandingService` 负责处理（默认是占位实现 `NoopMdImageUnderstandingService`，可切换成 `DashScopeMdImageUnderstandingService`）。它生成的 caption 会作为可检索文本，随 child 一起入库；图片资产的元数据存在 PostgreSQL 的 `md_document_asset` 表中，原图和原始 `.md` 则存在 MinIO。

**清理策略**：

- 删除 Markdown 文档时清理 `md_parent_chunk` / `md_child_chunk` / `md_document_asset` 记录、`md_kb_chunks` 向量及 MinIO 对象。
- 删除空间时由 MD 空间删除监听器清理该空间下全部 md 表与向量。
- MinIO 对象删除失败时记录 WARN 日志，不阻断数据库清理；需运维人员按 object key 做补偿清理。

**排查 SQL**：

```sql
-- 查看某空间的 Markdown 文档及状态
SELECT id, title, status, chunk_count, error_message, created_at
FROM md_documents
WHERE space_id = '空间UUID' AND deleted_at IS NULL
ORDER BY created_at DESC;

-- 查看某文档的 child 分块
SELECT id, parent_id, seq_in_parent, token_count
FROM md_child_chunk
WHERE document_id = '文档UUID'
ORDER BY seq_in_parent;
```

**上线检查**：

- 确认 MinIO bucket 可写，应用具备上传、预签名 URL、删除对象权限。
- 确认 Milvus `md_kb_chunks` 集合可用（由 `mdVectorStore` 在启动时 `initializeSchema`）。
- 默认图片理解为占位实现；接入真实 OCR/caption provider 后再评估并发、超时与重试参数。

### 10.8 生产环境注意事项

1. **JWT 密钥**：务必替换为高强度随机字符串，不能使用默认值
2. **数据库密码**：设置强密码，避免使用 `changeme`
3. **AI API Key**：建议通过 Kubernetes Secret 或 Vault 管理
4. **HTTPS**：生产环境建议在前置 Nginx/Traefik 上配置 TLS
5. **备份**：定期备份 `postgres_data` volume（向量数据可从文档重新生成）
6. **日志级别**：生产环境将 `com.enterprise.kb` 日志级别改为 `WARN`，减少输出

```yaml
# application.yml 生产配置调整
logging:
  level:
    com.enterprise.kb: WARN
    org.springframework.ai: WARN
```

### 10.9 Langfuse Tracing 运维

在线 LLM tracing 使用自部署 Langfuse，经 OpenTelemetry OTLP 上报。启动、初始化、登录、trace 验证和常见故障处理见：

- [Langfuse Tracing 启动调试操作手册](langfuse-tracing.md#第四部分--操作手册启动--调试)
- [Langfuse Tracing 验收报告](langfuse-tracing.md#第五部分--验收报告)
