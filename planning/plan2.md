# 企业知识库 — 扩展与优化规划（方向二）

> 与 `plan.md` 互补，侧重启用层面体验、合规安全、运维基础设施。每项注明涉及模块，方便直接对应开工。

---

## 一、前端体验与交互增强

### 1.1 搜索结果页增强

**现状**：`search.html` 已支持混合/语义/关键词三种模式，但结果页交互较为基础。

**可扩展**：
- **查询补全**（Query Autocomplete）— 输入时实时调用轻量语义模型返回候选词建议，减少用户打字成本
- **命中高亮**— 搜索结果摘要中用 `<mark>` 标签标亮匹配词，提升可读性
- ** facet 筛选**— 在结果区左侧增加标签/文件类型/日期范围的多维筛选面板，无需重新搜索即可过滤
- **搜索历史**— 记录当前用户在当前空间的最近 10 条搜索词，支持快速回填

---

### 1.2 知识图谱可视化

**现状**：`tags.html` 以树状列表展示标签，`/tags/graph` 接口返回节点和边数据，但无图谱可视化。

**扩展**：引入 D3.js 或 Cytoscape.js，在前端实现交互式知识图谱：
- 节点 = 标签（按类型着色：TAG / CATEGORY / ENTITY / TOPIC）
- 边 = 标签层级（parent→child）+ 文档关联（共同出现频次）
- 支持拖拽布局、点击节点展开邻居、搜索定位节点

---

### 1.3 文档在线预览

**现状**：文档上传后只能通过搜索看到片段，没有全文预览入口。

**扩展**：
- 在文档列表页增加"预览"按钮，点击后弹出 Modal 以只读视图展示文档内容
- PDF 使用 `pdf.js` 渲染，支持翻页和缩放
- Word/Markdown/TXT 使用等宽字体渲染，支持滚动
- 大文件（>5MB）提示用户下载查看，不在前端渲染

---

### 1.4 暗色模式（Dark Mode）

**现状**：前端仅有浅色主题，长时间使用的用户或偏好暗色的开发者体验欠佳。

**扩展**：
- 在 `app.css` 中用 CSS 变量定义两套色板（`--color-bg`, `--color-text` 等）
- 头部增加主题切换按钮，写入 `localStorage`
- 首次访问时跟随系统偏好（`prefers-color-scheme`）

---

### 1.5 文档变更通知

**现状**：文档被更新/删除后，已检索到旧版本的 RAG 答案会出现"答非所问"的情况，用户无感知。

**扩展**：
- 在 `document_chunks` 表增加 `content_hash` 字段，上传时计算存储
- 文档处理完成后比对新旧 hash，不一致时推送通知到该文档的关联提问者（通过会话历史反向关联）
- 前端在 AI 回答气泡旁增加"文档已更新"提示，点击可刷新相关问答

---

### 1.6 看板视图与批量管理

**现状**：文档列表仅有表格视图，批量操作仅支持删除。

**扩展**：
- 新增"看板"视图，按标签或处理状态（READY / PROCESSING / FAILED）分列展示文档卡片
- 卡片支持拖拽到不同列实现批量打标
- 多选后批量删除/批量重新处理/批量导出

---

## 二、安全与合规

### 2.1 文档级权限控制

**现状**：权限粒度仅到空间级别（VIEWER / EDITOR / ADMIN），同一空间内所有成员可访问全部文档。

**扩展**：
- 新增 `document_permissions` 表，记录 `document_id / user_id / permission`（READ / COMMENT / NONE）
- 文档上传时可设置公开范围：空间全员 / 指定成员 / 仅自己
- 搜索和问答时在 SQL 层面增加权限过滤条件（`DocumentPermissionChecker` 拦截器）
- 权限变更写 `audit_logs`，可追溯谁在何时访问了哪份文档

---

### 2.2 文档下载水印

**扩展**：当用户下载原始文档时（如从 MinIO 签发临时 URL），在后端对文件叠加动态水印：
- 水印内容包含 `下载者用户名 + 下载时间 + 空间名称`
- PDF 使用 iText 在每页右下角嵌入文本水印
- 水印对常规阅读影响小，但对截图传播有追溯威慑

---

### 2.3 SSO / OIDC 企业登录

**现状**：仅支持用户名密码本地注册登录，企业场景通常需要对接现有身份提供商。

**扩展**：
- 增加 Spring Security OAuth2 Client 支持，对接 Keycloak / Auth0 / 飞书 / 企业微信
- OIDC Provider 配置通过环境变量注入（`OIDC_ISSUER_URI`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`）
- 首次 OIDC 登录时自动创建本地用户记录，后续登录通过邮箱或 subject ID 关联

---

### 2.4 空间级 IP 访问控制

**扩展**：在 `spaces` 表增加 `allowed_ip_cidrs` 字段（JSONB 数组），空间管理员可配置允许访问的 IP 网段。后端在 `SpaceAccessInterceptor` 中校验请求来源 IP，不在白名单内返回 403。适用于金融/政务等对访问来源有严格要求的场景。

---

### 2.5 数据导出与 GDPR 合规

**扩展**：
- `GET /api/v1/users/{uid}/data-export` — 导出一个用户的所有个人数据（会话记录、搜索历史、标签操作）为 ZIP 包，满足 GDPR "数据可携带权"
- `DELETE /api/v1/users/{uid}` — 完全删除账户及其所有关联数据，包括 Redis 会话和 PostgreSQL 记录，不可恢复

---

## 三、搜索能力深化

### 3.1 查询改写（Query Expansion）可配置

**现状**：`QueryRewriteServiceImpl` 在每次问答前将用户问题改写为更适合检索的形式，但改写逻辑对用户不可见。

**扩展**：
- 页面在发送问题后、收到答案前，展示一行"已优化查询：xxx"，让用户感知改写行为
- 增加开关：`enterprise.kb.rag.query-rewrite-enabled`，关闭后直接使用原始问题
- 高级用户可手动编辑查询词，覆盖系统改写结果

---

### 3.2 语义相似文档推荐

**扩展**：在文档详情页或搜索结果页，增加"相似文档"区块：
- 取当前文档所有 chunk 的平均向量（或主 chunk 向量）
- 在 Milvus 中做余弦相似度 topK 查询
- 过滤掉自身，返回标题+摘要卡片，用户可直接点击跳转

---

### 3.3 多语言检索支持

**现状**：分词器配置固定，中文检索依赖内置切分，对英文/混合语言文档支持有限。

**扩展**：
- 引入 jieba 或 HanLP 做中文专业术语分词（补充 jtokkit 的 byte-level 分词）
- 支持按语言自动路由到不同 Embedding 模型（中文用 text-embedding-3-small 的中文语料权重版本）
- 检索结果按语言相关性加权排序

---

## 四、API 与集成

### 4.1 Webhook 事件通知

**扩展**：空间管理员可配置 Webhook URL（`spaces.allowed_webhooks`），当以下事件发生时 POST 通知：
- 文档上传成功 / 处理失败
- 空间有新成员加入
- 会话记录数超过阈值（监控 AI 使用量）

Payload 格式：
```json
{
  "event": "document.processed",
  "spaceId": "uuid",
  "documentId": "uuid",
  "status": "READY",
  "timestamp": "2026-04-28T10:00:00Z"
}
```
幂等处理：Webhook payload 携带 `eventId`（UUID），消费端可据此做去重。

---

### 4.2 API 速率限制精细化

**现状**：无接口限流，高并发或异常调用会导致 API 费用激增。

**扩展**：在现有 Spring Security Filter 链中加入 Bucket4j：
- 按 API Key（而非 IP）限流，支持不同套餐用户差异化配额
- 问答接口：10 次/分钟（普通用户）/ 60 次/分钟（管理员）
- 搜索接口：60 次/分钟（普通用户）/ 300 次/分钟
- 文档上传：20 次/小时
- 超限返回 `429` + `Retry-After` 头，前端展示友好限流提示

---

### 4.3 开放 API（面向外部应用）

**扩展**：参照 GitHub API 设计风格，将知识库核心能力以 RESTful API 形式开放：
- `GET /v1/spaces/{id}/documents` — 第三方应用获取文档列表
- `POST /v1/spaces/{id}/qa/ask` — 外部 Copilot 调用知识库问答
- Token 基于 `mcp_api_keys` 表的 API Key 认证，替代 JWT，签发给外部系统

---

### 4.4 Slack / 飞书 机器人集成

**扩展**：提供 Bot 应用模板，支持在 Slack 或飞书中 @机器人 直接向知识库提问，返回卡片式答案和引用来源：
- 机器人接收消息 → 调用 `/qa/ask` 接口 → 格式化返回卡片
- 群组内 @机器人 提问，自动关联当前群的 space（通过 Bot 安装时的上下文传入）

---

## 五、数据治理与质量

### 5.1 文档质量评分

**扩展**：文档进入 READY 状态后，运行质量评估 pipeline，评估维度：
- **完整性**— chunk 数是否过少（信息量不足）或过多（过度碎片化）
- **可检索性**— 标题是否清晰、摘要是否包含关键词
- **新鲜度**— 文档距今是否超过 N 天未更新（提示管理员复核）

评分写入 `documents.quality_score` 字段，搜索结果可按质量分数加权，优先展示高质量文档。

---

### 5.2 重复文档检测

**扩展**：文档上传后计算 `content_hash` 并在空间内查重：
- 完全相同（hash 一致）：提示用户是否合并或跳过
- 高度相似（chunk 重叠率 > 80%）：提示可能为重复版本，供用户确认

---

### 5.3 搜索分析仪表盘

**扩展**：利用 `audit_logs` 表记录搜索事件，在管理员仪表盘增加搜索分析区块：
- Top 10 高频查询词（了解用户关注热点）
- 无结果查询（识别知识盲区，指导文档补充方向）
- 问答平均响应时间趋势
- 各模型调用占比（DashScope / MiniMax / OpenAI）

---

### 5.4 文档自动摘要

**扩展**：文档处理完成后，异步调用 LLM 生成：
- 文档摘要（200 字）— 存入 `documents.summary` 字段，文档列表页和搜索结果摘要优先展示摘要而非片段
- 关键词标签（3-5 个）— 追加到 `document_tags`，丰富检索入口

---

## 六、基础设施

### 6.1 PostgreSQL 读写分离

**现状**：所有读写操作访问同一个 PostgreSQL 实例，文档量大时查询性能成为瓶颈。

**扩展**：
- 引入 PgBouncer 作为连接池，配置为主从模式
- 读操作（搜索结果列表、文档查询）路由到只读副本
- 写操作（上传、处理、会话记录）保持主库
- 副本延迟监控：超过 5 秒告警

---

### 6.2 Milvus 多 Collection 分区

**现状**：所有空间的向量存储在同一个 Milvus collection（按 `spaceId` 字段过滤）。

**扩展**：
- 文档量超过 500 万 chunk 后，按空间拆分 collection：`kb_vectors_{spaceSlug}`
- 减少向量检索时 `spaceId` 过滤的数据量，降低搜索延迟
- 新增 collection 前在 `application.yml` 配置项声明，由 `VectorStoreServiceImpl` 按名称动态路由

---

### 6.3 配置中心化

**现状**：所有配置分散在 `.env` 文件和环境变量中，不同环境的配置通过不同 .env 管理，容易出错。

**扩展**：
- 引入 Spring Cloud Config 或 Nacos 作为配置中心
- 将非敏感配置（chunk size、RRF_K、TTL 等）迁移到 `config.yaml`，敏感配置（API Key、密码）保留在 Nacos 的密文配置或 Vault
- 支持运行时动态刷新：修改配置后无需重启应用（`@RefreshScope`）

---

### 6.4 多租户 SaaS 改造

**扩展**：如果未来面向多租户 SaaS 场景，需改造：
- 数据隔离：每个租户独立的 PostgreSQL schema 或独立数据库实例
- 资源配额：`spaces` 表增加 `max_documents`、`max_storage_mb` 字段，超额拒绝上传
- 计费集成：预留 `subscription_plan` 字段，对接 Stripe 订阅回调

---

### 6.5 健康检查增强

**现状**：Actuator `/health` 仅检查应用存活，不验证下游依赖。

**扩展**：自定义 `HealthIndicator`：
- PostgreSQL 连接 + 简单查询（`SELECT 1`）
- Redis 连接 + PING
- Milvus 连接 + collection 数量检查
- MinIO 连接 + bucket 存在性检查
- 各 AI Provider API Key 可达性（轻量级 `/models` 接口探测）

任意依赖不健康时，`/health` 返回 `DOWN`，容器 `HEALTHCHECK` 即可感知并自动重启。
