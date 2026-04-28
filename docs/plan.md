# 企业知识库 — 扩展与优化规划

> 基于当前代码现状（2026-04）梳理，聚焦可落地的方向。每项注明涉及的代码位置，方便直接开工。

---

## 一、RAG 能力增强

### 1.1 流式问答接入前端

**现状**：后端 `QnAServiceImpl.askStream()` 已实现 SSE 流式接口（`/qa/ask/stream`），但前端 `qa.html` 仍使用阻塞式 `/qa/ask`，用户需等待完整响应才能看到内容。

**优化**：前端改用 `EventSource` 或 `fetch` + `ReadableStream` 消费 SSE，实现打字机逐字输出效果，显著改善大模型长回答的等待体验。

**注意**：流式接口当前没有 `sessionId` 返回和 citations 输出，接入流式前需先在后端补齐这两个字段的带外返回（可通过首条 SSE 事件携带元数据）。

---

### 1.2 图谱增强检索（Graph RAG）

**现状**：`document_relations` 表已记录文档间关联，`tags` 表有完整的标签树，但检索阶段完全绕过这两张表，仅做向量+关键词匹配。

**扩展**：在 `HybridSearchServiceImpl` 中加入第三路 **图谱扩散检索**：
1. 先做混合检索拿到初始命中集
2. 查询 `document_relations` 找到与命中文档强关联的邻居文档
3. 将邻居文档的高分 chunk 追加进候选池，再整体 Rerank

这样可以找到跨文档的隐性关联知识，对"A 依赖 B，B 依赖 C"类问题效果显著。

---

### 1.3 Rerank 模型可插拔

**现状**：`RerankServiceImpl` 硬绑定 DashScope `gte-rerank`，换模型需改代码。

**扩展**：参照 `ModelProviderResolver` 的多模型模式，提取 `RerankProvider` 枚举，支持 DashScope / Cohere / 本地 BGE-Reranker，通过配置项 `enterprise.kb.ai.rerank-provider` 切换，降级时兜底返回原始 RRF 结果（当前已有此逻辑）。

---

### 1.4 分块策略可选

**现状**：`ChunkingServiceImpl`（固定 token 滑窗）和 `SentenceAwareChunkingServiceImpl`（句子感知）已共存，但上层 `DocumentIngestionPipeline` 固定注入其中一个。

**扩展**：在文档元数据或空间配置中增加 `chunkStrategy` 字段（`TOKEN_WINDOW` / `SENTENCE` / `SEMANTIC`），`DocumentIngestionPipeline` 根据策略按名称选取对应 Bean，让不同类型文档使用最合适的分块方式。

---

### 1.5 多轮对话上下文压缩

**现状**：`RedisChatMemory` 超出 20 条消息时直接截断最早的记录，丢失早期上下文。

**优化**：超出阈值时不做截断，而是调用 LLM 对历史消息做摘要压缩（Summary Memory），将 N 条历史压成 1 条摘要消息保留，兼顾上下文完整性和 token 消耗。

---

### 1.6 Agentic RAG 工具扩展

**现状**：`AgenticQnAServiceImpl` 中 `ReactAgent` 只注册了 `searchKnowledgeBase` 一个工具。

**可扩展工具**：
- `searchByTag(tagName)` — 按标签筛选文档再检索，适合"查所有'重要'文档中关于X的内容"
- `getDocumentDetail(documentId)` — 获取某篇文档的完整摘要或目录，适合精读场景
- `calculateOrFormat(expression)` — 结构化数据计算（适合财务/数据类知识库）

每个工具只需实现 `FunctionToolCallback`，现有框架完全支持，无需改动 Agent 框架本身。

---

## 二、功能扩展

### 2.1 MCP Server 接入

**现状**：数据库中已建 `mcp_api_keys` 表（migration 013），但相关逻辑尚未实现。

**扩展**：基于该表实现标准 MCP（Model Context Protocol）Server，将知识库检索能力暴露为工具：
- `search_knowledge` — 混合检索
- `ask_question` — RAG 问答

外部 AI 应用（Claude Desktop、Cursor、自研 Copilot）即可通过 MCP 协议直接调用，无需集成 REST API。

---

### 2.2 更多文档格式支持

**现状**：支持 PDF、Word、Markdown、TXT、HTML。

**可扩展**：
- Excel/CSV — 表格数据转结构化 Markdown 再分块，保留列名语义
- PowerPoint — 按幻灯片切分，标题作为元数据
- 网页爬取 — 接受 URL 输入，调用 Playwright/Jsoup 抓取正文后走现有 pipeline

扩展点：`DocumentParserService` 使用策略模式按 MIME 类型分派，新增格式只需新增一个 Parser 实现。

---

### 2.3 会话导出与分享

**现状**：`qa_messages` 表已持久化完整对话，但没有导出入口。

**扩展**：
- `GET /spaces/{sid}/qa/sessions/{sessionId}/export?format=markdown` — 导出为 Markdown 文件，包含问答内容和引用来源
- 分享链接（只读 token）— 生成带过期时间的只读链接，无需登录即可查看某次对话

---

### 2.4 文档自动打标增强

**现状**：`kb-knowledge-graph` 模块已有自动打标，但触发时机和打标质量待确认。

**扩展**：文档进入 `READY` 状态后，异步触发基于 LLM 的实体抽取和主题分类，自动将结果写入 `document_tags`，并在 `tags` 表中按需创建新标签（已有 `ENTITY` / `TOPIC` 类型），丰富知识图谱密度。

---

### 2.5 问答反馈与质量追踪

**扩展**：在 `qa_messages` 表增加 `feedback` 字段（`GOOD` / `BAD` / `NULL`），前端在 AI 回答气泡下方添加点赞/踩按钮。数据积累后可用于：
- 识别高频答错的问题类型
- 筛选优质问答对做 fine-tune 数据集
- 监控模型切换前后的质量变化

---

## 三、性能优化

### 3.1 检索结果缓存

**现状**：每次搜索都实时调用向量数据库和 PostgreSQL，相同问题重复计算。

**优化**：在 `HybridSearchServiceImpl.search()` 入口加 Redis 缓存，key 为 `search:{spaceId}:{queryHash}:{topK}`，TTL 5 分钟。文档更新时主动 invalidate 对应空间的缓存前缀。高频重复查询（如仪表盘展示）收益明显。

---

### 3.2 向量化批处理

**现状**：文档分块后逐条调用 Embedding API，网络 RTT 是主要瓶颈。

**优化**：`VectorStoreServiceImpl` 中将 chunk 列表分批（batch size=32）并行提交 Embedding，利用模型 API 的批处理能力，向量化耗时预计降低 60%+。

---

### 3.3 Milvus 索引类型调优

**现状**：默认使用 `FLAT` 索引（精确搜索），数据量小时没问题，百万级 chunk 后延迟会上升。

**优化**：chunk 数超过 50 万后切换为 `HNSW`（近似最近邻），配置 `m=16, efConstruction=200`，召回率损失 < 1%，查询速度提升 10x 以上。切换无需重新向量化，只需重建索引。

---

### 3.4 QueryRewrite + HyDE 降级开关

**现状**：`QnAServiceImpl` 每次问答都串行执行 QueryRewrite → HyDE，两次 LLM 调用（约 500-1000ms）叠加在用户等待路径上。

**优化**：
1. 两者并行执行（`CompletableFuture.allOf`），节省约 400-600ms
2. 增加配置开关 `enterprise.kb.rag.query-rewrite-enabled` / `hyde-enabled`，空间管理员可按需关闭，简单问题不必走完整 pre-retrieval 链路

---

### 3.5 会话列表查询优化

**现状**：`QaChatSessionMapper.findBySpaceIdAndUserId` 使用子查询计算 `message_count`，在会话数量多时每条都触发一次子查询。

**优化**：在 `qa_sessions` 表增加 `message_count` 冗余字段，每次插入消息时 `UPDATE ... SET message_count = message_count + 1`，消除子查询，列表接口降为单表扫描。

---

## 四、工程质量

### 4.1 集成测试覆盖 RAG 链路

**现状**：缺少针对 `QnAServiceImpl` 和 `AgenticQnAServiceImpl` 的集成测试，Rerank/HyDE 调用全靠手动自测。

**建议**：使用 `@SpringBootTest` + Testcontainers（PostgreSQL + Redis），mock AI 模型调用（`MockChatModel` / Wiremock），覆盖以下场景：
- 正常问答返回 citations
- sessionId 延续多轮对话
- Rerank 失败时降级返回 RRF 结果
- 空间无文档时明确告知用户

---

### 4.2 全局限流

**现状**：问答接口无任何限流，单用户可无限并发调用 LLM，产生预期外的 API 费用。

**优化**：在 `QnAController` 加 Bucket4j 令牌桶限流，按 `userId` 隔离，策略建议：
- 问答接口：10 次/分钟
- 搜索接口：60 次/分钟

超限返回 `429 Too Many Requests`，`Retry-After` 头携带等待秒数。

---

### 4.3 敏感配置校验

**现状**：`JWT_SECRET` 等敏感配置在 `.env` 中，但启动时没有长度/格式校验，配置错误只在运行时暴露。

**优化**：在 `AppConfig` 或专属 `ConfigValidator` Bean 中用 `@PostConstruct` 校验：JWT 密钥 ≥ 32 字节、至少一个 AI Provider Key 已配置、Redis 连接可达。校验失败快速 fail-fast，`System.exit(1)` 并打印明确错误信息，避免启动后难以排查的运行时异常。

---

### 4.4 异步文档处理进度推送

**现状**：文档上传后前端通过轮询文档列表接口查询状态，每隔几秒发一次请求。

**优化**：引入 SSE 或 WebSocket 推送文档处理进度，`DocumentIngestionPipeline` 完成各阶段时发布 Spring 事件，事件监听器将进度推送到前端，减少不必要的轮询请求。

---

## 五、运维与可观测性

### 5.1 结构化日志与 TraceId

**现状**：日志为文本格式，多模块并发时难以追踪单次请求的完整链路。

**优化**：引入 MDC（Mapped Diagnostic Context），在请求入口（Filter 或 Interceptor）注入 `traceId`（UUID 或 W3C Trace Context），所有 `log.info/warn/error` 自动携带 traceId，便于在 ELK/Loki 中按 traceId 过滤单次请求的完整日志。

---

### 5.2 AI 调用监控指标

**现状**：LLM 调用的延迟、token 消耗、失败率没有指标暴露，出问题只能翻日志。

**优化**：在 `ModelProviderResolver` 包装层用 Micrometer 记录：
- `kb.llm.latency`（按 provider/model tag）
- `kb.llm.tokens.total`（按 provider/operation=embedding|chat）
- `kb.llm.errors.total`

通过 `/actuator/metrics` 或 Prometheus + Grafana 实时监控 AI 成本与稳定性。

---

### 5.3 会话自动清理

**现状**：`qa_sessions` 的软删除记录和长期不活跃会话会无限积累，`qa_messages` 数据量随之增长。

**优化**：定时任务（`@Scheduled` 或 Quartz）每天凌晨执行：
1. 物理删除 `deleted_at IS NOT NULL` 且超过 30 天的会话和关联消息
2. 对超过 90 天未活跃的会话标记软删除

---

### 5.4 向量数据与元数据一致性检查

**现状**：文档删除时同步清理 Milvus 向量，但异常中断可能导致两边不一致——PostgreSQL 已删除但 Milvus 残留孤儿向量，或反过来。

**优化**：定期一致性巡检任务，对比 `document_chunks.milvus_id` 集合与 Milvus 实际存储的 ID，打印 diff 日志并可选自动清理，保障长期运行后数据可信度。

---

### 5.5 多节点部署支持

**现状**：文档处理使用 `@Async` 线程池，多实例部署时同一文档可能被多个节点重复处理。

**优化**：在 `DocumentServiceImpl` 的异步处理入口加 Redis 分布式锁（`SETNX documentId TTL=10min`），只有拿到锁的节点执行 pipeline，其他节点跳过。解锁在 `finally` 块中执行，锁超时自动释放防止死锁。
