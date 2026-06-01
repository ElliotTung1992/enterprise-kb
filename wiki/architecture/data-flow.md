# 数据流

## Markdown 文档上传流

```
POST /api/v1/spaces/{spaceId}/md-documents/upload
    │
    ▼ MdDocumentController
    │  校验文件类型为 .md · 上传至 MinIO（object_key）
    │
    ▼ MdDocumentService.upload()
    │  插入 md_documents 表（status=PENDING）
    │  触发 MdDocumentIngestionWorker.ingest()
    │
    ▼ MdDocumentIngestionWorker  ← @Async("ingestionExecutor")，virtual thread
    │
    ├─ Stage 1: 下载 .md from MinIO → 本地临时文件
    │
    ├─ Stage 2: MarkdownStructureIngestionService.parse()
    │   ├─ 按 H1-H3 切 parent（整段 section）
    │   ├─ parent 内再切段落级 child（small-to-big）
    │   ├─ 表格双表示（检索用线性化 embed_text，返回用原始 markdown）
    │   ├─ 代码块独立 child
    │   └─ 图片 → MdImageUnderstandingService（Noop / DashScope 可切换）
    │      → 写 md_document_asset + IMAGE_CAPTION 类型 child
    │
    ├─ Stage 3: 写 md_parent_chunk + md_child_chunk + md_document_asset（PG）
    │
    └─ Stage 4: MdVectorStoreService.upsert()
        ├─ 对 child.embed_text 调 EmbeddingModel（DashScope, 1536 维）
        └─ 写入 Milvus collection md_kb_chunks
        status → READY（或 FAILED）
```

## 问答请求流（标准 md QA）

```
POST /api/v1/spaces/{spaceId}/md-qa/ask
    │
    ▼ MdQnAController → MdQnAService.ask()
    │
    ├─ MdHybridSearchService.search()
    │   ├─ MdVectorSearchService（md_kb_chunks 向量检索） ─┐ CompletableFuture 并行
    │   └─ MdKeywordSearchService（pg_trgm 或 BM25 on md_child_chunk） ─┘
    │      RRF（k=60）child 粒度融合，不去重
    │
    ├─ [可选] RerankService.rerank()（DashScope gte-rerank, child 粒度精排）
    │
    ├─ MdParentExpansionService（topK child 折叠 parentId → 回查整段 parent + 多窗节选）
    │
    ├─ ChatClient.prompt() → LLM 生成答案
    │
    └─ QaChatSessionService.saveExchange()
        ├─ 首次: 创建 qa_sessions 记录（title = 首条问题截断 50 字）
        └─ 后续: 追加 qa_messages + 刷新 updated_at
```

## 问答请求流（Agentic md QA）

```
POST /api/v1/spaces/{spaceId}/md-qa/ask/agentic
    │
    ▼ MdAgenticQnAService.ask()
    │
    └─ ReactAgent（Spring AI Alibaba）
        系统提示: 你是 md 知识库助手
        工具:
          ├─ searchKnowledgeBase(query, topK)
          │      └─► MdHybridSearch → Rerank → 返回 child 命中
          └─ readFullSection(parentId)
                 └─► 按 parentId 回查整段 parent 原文
        LLM 自主决定: 搜什么 · 搜几次 · 是否回查 parent · 何时停止
        TokenBudgetService 控制最大 token 消耗
        会话历史: RedisChatMemory（key=session:{sessionId}）
```

## 客服助手两层域路由流（kill-switch 默认 OFF）

```
POST /api/v1/customer-assistant/chat
    │
    ▼ CustomerAssistantServiceImpl.routedChat()
    │
    ├─ 攻击守卫
    ├─ 状态 / 硬规则（awaiting_slot → 跳过路由）
    ├─ DomainRouterService（Tier-1, 一次路由模型调用，默认 LLAMA_CPP）
    │   → PRIMARY | SECONDARY_CSV | RUNNER_UP | EVIDENCE
    │   证据不足 → UNCLEAR
    ├─ DomainHandler 分派（Tier-2）
    │   ├─ AfterSalesDomainHandler（2 工具 + HITL）
    │   └─ ComplaintDomainHandler（触发投诉 StateGraph）
    ├─ secondary 反问
    └─ 持久化 customer_messages（含 domain 列）
```

详见 [[features/intent-routing]]。

## 权限检查流

```
任意需鉴权接口
    │
    ▼ JwtAuthenticationFilter
    │  解析 Bearer Token → 设置 SecurityContext
    │
    ▼ @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    │
    ▼ SpacePermissionEvaluator.hasPermission()
        username → UserService.getUserIdByUsername()
        → SpaceService.hasRole(userId, spaceId, VIEWER/EDITOR/OWNER)
        → 查询 user_space_roles 表
```

> 原标准 RAG 上传 / 问答流（`/documents` 上传 → `DocumentIngestionPipeline` → `kb_chunks` + `document_chunks` → `QnAService` / `AgenticQnAService` → `/qa/ask` / `/qa/ask/advanced`）已随迁移 031 整体退役。
