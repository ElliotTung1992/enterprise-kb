# 企业知识库 — 功能操作文档

## 目录

1. [快速开始](#1-快速开始)
2. [账号与认证](#2-账号与认证)
3. [空间管理](#3-空间管理)
4. [文档管理](#4-文档管理)
5. [搜索](#5-搜索)
6. [AI 问答](#6-ai-问答)
7. [标签与知识图谱](#7-标签与知识图谱)
8. [系统管理（管理员）](#8-系统管理管理员)
9. [API 参考](#9-api-参考)
10. [部署与运维](#10-部署与运维)

---

## 1. 快速开始

### 系统架构

```
浏览器
  │
  └─► Spring Boot 应用 (8081)
          │
          ├─► PostgreSQL 5432   — 用户/空间/文档元数据、会话记录
          ├─► Redis 6379        — 会话上下文（LLM 多轮记忆，24h TTL）
          ├─► Milvus 19530      — 向量索引（语义搜索）
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
DASHSCOPE_API_KEY=your_dashscope_key      # 默认 AI 提供商——通义千问（必填）

# 可选 AI 提供商（至少配置一个）
MINIMAX_API_KEY=
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

**空间**是知识库的基本隔离单元。每个空间有独立的文档、标签和成员权限。

### 空间角色说明

| 角色 | 权限 |
|------|------|
| VIEWER | 查看文档、搜索、AI 问答 |
| EDITOR | 上传/删除文档、创建/删除标签 |
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

> 需要 ADMIN 角色。删除后空间内所有文档、标签、向量数据将一并删除，操作不可逆。

**接口**：`DELETE /api/v1/spaces/{spaceId}`

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId} \
  -H "Authorization: Bearer {accessToken}"
```

---

## 4. 文档管理

### 4.1 上传文档

**页面操作**：

1. 访问 http://localhost:8081/upload.html
2. 在顶部选择目标空间
3. 将文件拖入上传区域，或点击"选择文件"
4. 支持同时选择多个文件
5. 点击"上传"，页面会实时显示处理进度

**支持格式**：

| 格式 | MIME 类型 |
|------|-----------|
| PDF | application/pdf |
| Word (.docx) | application/vnd.openxmlformats-officedocument.wordprocessingml.document |
| Word (.doc) | application/msword |
| Markdown (.md) | text/markdown, text/x-markdown |
| 纯文本 (.txt) | text/plain |
| HTML | text/html |

**限制**：单文件最大 100 MB。

**单文件上传接口**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/documents/upload \
  -H "Authorization: Bearer {accessToken}" \
  -F "file=@/path/to/document.pdf"
```

**批量上传接口**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/documents/upload/batch \
  -H "Authorization: Bearer {accessToken}" \
  -F "files=@doc1.pdf" \
  -F "files=@doc2.docx"
```

上传接口返回 `202 Accepted`，文档将在后台异步处理。

### 4.2 文档处理状态

文档上传后经历以下状态：

```
PENDING（已接收）→ PROCESSING（解析/分块/向量化）→ READY（可搜索）
                                                  └─► FAILED（处理失败）
```

**处理过程说明**：

1. **解析**：提取文本内容（PDF 按页，Word 按段落，Markdown 保留结构）
2. **分块**：按 512 token 切分，相邻块有 64 token 重叠
3. **向量化**：调用 AI 提供商生成 1536 维嵌入向量
4. **存储**：元数据写入 PostgreSQL，向量写入 Milvus

### 4.3 查看文档列表

**页面操作**：仪表盘选择空间后，下方"最近文档"区域显示最新 10 条记录。

**接口**：支持分页、状态筛选、关键词搜索

```bash
# 列出所有文档（分页）
curl "http://localhost:8081/api/v1/spaces/{spaceId}/documents?page=0&size=20" \
  -H "Authorization: Bearer {accessToken}"

# 按状态筛选
curl "http://localhost:8081/api/v1/spaces/{spaceId}/documents?status=READY" \
  -H "Authorization: Bearer {accessToken}"

# 按关键词搜索文档标题
curl "http://localhost:8081/api/v1/spaces/{spaceId}/documents?keyword=技术规范" \
  -H "Authorization: Bearer {accessToken}"

# 按创建时间倒序
curl "http://localhost:8081/api/v1/spaces/{spaceId}/documents?sort=createdAt,desc" \
  -H "Authorization: Bearer {accessToken}"
```

**返回字段说明**：

| 字段 | 说明 |
|------|------|
| id | 文档 UUID |
| title | 文档标题 |
| originalFilename | 原始文件名 |
| mimeType | 文件类型 |
| fileSizeBytes | 文件大小（字节） |
| status | PENDING / PROCESSING / READY / FAILED |
| chunkCount | 分块数量 |
| errorMessage | 处理失败时的错误信息 |
| createdAt | 上传时间 |

### 4.4 查看文档详情

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId} \
  -H "Authorization: Bearer {accessToken}"
```

### 4.5 重新处理文档

当文档处于 FAILED 状态，或需要更新分块/向量时使用：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId}/reprocess \
  -H "Authorization: Bearer {accessToken}"
```

返回 `202 Accepted`，后台重新执行解析→分块→向量化流程。

### 4.6 删除文档

> 需要 EDITOR 角色。删除后同步清除 Milvus 中的向量数据，操作不可逆。

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId} \
  -H "Authorization: Bearer {accessToken}"
```

### 4.7 文档关联

将两份相关文档建立关联关系，便于知识图谱导航：

**查看关联**：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId}/relations \
  -H "Authorization: Bearer {accessToken}"
```

**添加关联**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/documents/{docId}/relations \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"targetDocumentId": "目标文档UUID", "relationType": "REFERENCES"}'
```

---

## 5. 搜索

### 5.1 三种搜索模式

**页面操作**：

1. 访问 http://localhost:8081/search.html
2. 在顶部选择目标空间
3. 选择搜索模式（默认混合搜索）
4. 输入查询内容，按回车或点击搜索按钮

| 模式 | 原理 | 适用场景 |
|------|------|----------|
| 混合搜索（推荐） | 关键词 + 语义融合排序 | 通用场景，效果最佳 |
| 语义搜索 | 向量相似度（COSINE） | 自然语言提问、概念检索 |
| 关键词搜索 | 全文检索（BM25） | 精确词匹配、技术名词 |

### 5.2 搜索接口

**混合搜索**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/search/hybrid \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "query": "如何配置数据库连接池",
    "topK": 10
  }'
```

**语义搜索**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/search \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"query": "连接池最大连接数配置", "topK": 5}'
```

**关键词搜索**：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/search/keyword \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{"query": "HikariCP maximum-pool-size", "topK": 10}'
```

### 5.3 搜索请求参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| query | string | 是 | 搜索内容 |
| topK | integer | 否 | 返回结果数（默认 10，常用 5/10/20） |
| modelProvider | string | 否 | 指定 AI 提供商（MINIMAX/OPENAI/ANTHROPIC） |
| filters.tagIds | array | 否 | 按标签筛选 |
| filters.dateFrom | string | 否 | 上传日期起始（ISO 8601） |
| filters.dateTo | string | 否 | 上传日期截止（ISO 8601） |
| filters.mimeTypes | array | 否 | 按文件类型筛选 |

### 5.4 搜索结果说明

```json
{
  "data": {
    "hits": [
      {
        "chunkId": "uuid",
        "documentId": "uuid",
        "documentTitle": "数据库配置指南.pdf",
        "excerpt": "HikariCP 连接池最大连接数通过 maximum-pool-size 参数配置，默认值为 10...",
        "pageNumber": 5,
        "score": 0.92,
        "mimeType": "application/pdf"
      }
    ],
    "totalHits": 23,
    "searchMode": "hybrid",
    "durationMs": 145
  }
}
```

| 字段 | 说明 |
|------|------|
| excerpt | 包含匹配内容的文本片段 |
| score | 相关度评分（0-1，越高越相关） |
| pageNumber | PDF 文档的页码 |
| durationMs | 搜索耗时（毫秒） |

---

## 6. AI 问答

### 6.1 功能说明

AI 问答基于 RAG（检索增强生成）技术：先从知识库检索相关内容，再由 AI 模型结合上下文生成回答。回答会附带来源引用，确保可溯源、可验证。

### 6.2 页面操作

1. 访问 http://localhost:8081/qa.html
2. 在顶部选择目标空间（切换空间会自动开始新对话）
3. 选择 AI 模型（默认通义千问 / MiniMax / GPT-4o / Claude）
4. 设置上下文块数（默认 5，越多参考内容越丰富，但消耗更多 Token）
5. 在输入框输入问题，按 Enter 发送（Shift+Enter 换行）
6. 查看底部"引用来源"面板了解回答依据
7. 左侧会话列表展示当前空间的历史对话，点击可切换查看
8. 点击左上角"+ 新对话"开始全新会话

### 6.3 支持的 AI 模型

| 提供商 | 模型 | 特点 | 需要密钥 |
|--------|------|------|----------|
| DashScope（默认） | qwen-max | 通义千问，中文理解强 | DASHSCOPE_API_KEY |
| MiniMax | abab6.5s-chat | 中英文均衡，性价比高 | MINIMAX_API_KEY |
| OpenAI | gpt-4o | 英文推理能力强 | OPENAI_API_KEY |
| Anthropic | Claude Sonnet | 长文本理解出色 | ANTHROPIC_API_KEY |

### 6.4 问答接口（阻塞式）

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/qa/ask \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "我们的数据库连接池默认最大连接数是多少？",
    "modelProvider": "MINIMAX",
    "topK": 5
  }'
```

**返回示例**：

```json
{
  "data": {
    "answer": "根据知识库中的配置文档，数据库连接池（HikariCP）的默认最大连接数为 **20**，最小空闲连接数为 **5**，连接超时时间为 **30 秒**。\n\n> 如需修改，请在 `application.yml` 的 `spring.datasource.hikari` 节点下调整 `maximum-pool-size` 参数。",
    "sessionId": "session-uuid",
    "citations": [
      {
        "documentId": "uuid",
        "documentTitle": "数据库配置指南.pdf",
        "excerpt": "maximum-pool-size: 20\nminimum-idle: 5\nconnection-timeout: 30000",
        "pageNumber": 3,
        "score": 0.94
      }
    ],
    "modelUsed": "MINIMAX",
    "tokensUsed": 856
  }
}
```

### 6.5 连续对话（多轮问答）

将上一次返回的 `sessionId` 传入下一次请求，AI 会记住对话上下文：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/qa/ask \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "question": "那么最小空闲连接数呢？",
    "sessionId": "上一次返回的 session-uuid",
    "modelProvider": "MINIMAX",
    "topK": 5
  }'
```

### 6.6 流式问答（逐字输出）

适合在自定义前端中实现打字机效果：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/qa/ask/stream \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"question": "请介绍一下系统架构", "topK": 5}'
```

接口返回 Server-Sent Events 格式，前端使用 `EventSource` 接收。

### 6.7 会话管理

每次问答均自动创建或续写会话记录（标题取问题前 50 字），无需手动操作。以下接口用于在会话列表页面展示和管理历史对话。

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

## 7. 标签与知识图谱

### 7.1 标签类型说明

| 类型 | 说明 | 示例 |
|------|------|------|
| TAG | 普通标签/分类 | "重要"、"待归档" |
| CATEGORY | 分类目录（可含子标签） | "研发"、"产品" |
| ENTITY | 命名实体（人物/产品/公司） | "Spring Boot"、"张三" |
| TOPIC | 主题/议题 | "性能优化"、"安全审计" |

### 7.2 页面操作

1. 访问 http://localhost:8081/tags.html
2. 在顶部选择目标空间
3. 左侧标签树展示当前空间的所有标签（树状层级结构）
4. 点击"新建标签"按钮创建标签
5. 标签卡片右侧的删除图标可删除该标签

### 7.3 创建标签

**接口**：`POST /api/v1/spaces/{spaceId}/tags`

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/tags \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "性能优化",
    "tagType": "TOPIC",
    "color": "#10b981",
    "description": "与系统性能相关的文档"
  }'
```

**创建子标签**（指定 parentId）：

```bash
curl -X POST http://localhost:8081/api/v1/spaces/{spaceId}/tags \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "数据库优化",
    "tagType": "TOPIC",
    "parentId": "父标签UUID",
    "color": "#6366f1"
  }'
```

### 7.4 查看标签列表

**平铺列表**：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/tags \
  -H "Authorization: Bearer {accessToken}"
```

**树状结构**：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/tags/tree \
  -H "Authorization: Bearer {accessToken}"
```

### 7.5 合并标签

将两个标签合并（将 sourceTag 的所有关联转移至 targetTag，并删除 sourceTag）：

```bash
curl -X POST "http://localhost:8081/api/v1/spaces/{spaceId}/tags/{sourceTagId}/merge/{targetTagId}" \
  -H "Authorization: Bearer {accessToken}"
```

### 7.6 删除标签

```bash
curl -X DELETE http://localhost:8081/api/v1/spaces/{spaceId}/tags/{tagId} \
  -H "Authorization: Bearer {accessToken}"
```

### 7.7 获取知识图谱数据

返回标签节点和关联边，可用于前端图谱可视化：

```bash
curl http://localhost:8081/api/v1/spaces/{spaceId}/tags/graph \
  -H "Authorization: Bearer {accessToken}"
```

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

#### 文档

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/spaces/{sid}/documents | 文档列表（分页） |
| POST | /api/v1/spaces/{sid}/documents/upload | 上传单文件 |
| POST | /api/v1/spaces/{sid}/documents/upload/batch | 批量上传 |
| GET | /api/v1/spaces/{sid}/documents/{did} | 文档详情 |
| DELETE | /api/v1/spaces/{sid}/documents/{did} | 删除文档 |
| POST | /api/v1/spaces/{sid}/documents/{did}/reprocess | 重新处理 |
| GET | /api/v1/spaces/{sid}/documents/{did}/relations | 文档关联 |
| POST | /api/v1/spaces/{sid}/documents/{did}/relations | 添加关联 |

#### 搜索

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/spaces/{sid}/search | 语义搜索 |
| POST | /api/v1/spaces/{sid}/search/keyword | 关键词搜索 |
| POST | /api/v1/spaces/{sid}/search/hybrid | 混合搜索 |

#### AI 问答

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/spaces/{sid}/qa/ask | 问答（阻塞式） |
| POST | /api/v1/spaces/{sid}/qa/ask/stream | 问答（流式 SSE） |
| GET | /api/v1/spaces/{sid}/qa/sessions | 会话列表 |
| GET | /api/v1/spaces/{sid}/qa/sessions/{sessionId}/messages | 会话消息列表 |
| PATCH | /api/v1/spaces/{sid}/qa/sessions/{sessionId}/title | 重命名会话 |
| DELETE | /api/v1/spaces/{sid}/qa/sessions/{sessionId} | 删除会话 |

#### 标签

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/spaces/{sid}/tags | 标签列表 |
| GET | /api/v1/spaces/{sid}/tags/tree | 标签树 |
| POST | /api/v1/spaces/{sid}/tags | 创建标签 |
| DELETE | /api/v1/spaces/{sid}/tags/{tid} | 删除标签 |
| POST | /api/v1/spaces/{sid}/tags/{tid}/merge/{tid2} | 合并标签 |
| GET | /api/v1/spaces/{sid}/tags/graph | 知识图谱数据 |

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

当前配置使用 1536 维向量（与 OpenAI text-embedding-3-small 和 MiniMax embo-01 兼容）。如需修改，需同步更新 `application.yml` 中的 `embedding-dimension` 并**重建** Milvus collection（现有数据需重新向量化）。

### 10.6 文件存储路径

原始上传文件存储在容器内的 `/app/uploads`，挂载到 Docker volume `uploads_data`。可通过环境变量 `FILE_STORAGE_PATH` 修改路径。

### 10.7 生产环境注意事项

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
