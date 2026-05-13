# 数据流

## 文档上传流

```
POST /api/v1/spaces/{spaceId}/documents
    │
    ▼ DocumentController
    │  校验 MIME 类型白名单 · 保存文件到 uploads/
    │
    ▼ DocumentService.upload()
    │  插入 documents 表 (status=PENDING)
    │  发布 ApplicationEvent
    │
    ▼ DocumentIngestionPipeline.ingest()  ← @Async("ingestionExecutor") 虚拟线程
    │
    ├─ Stage 1: DocumentParserService.parse()
    │   └─ 按 MIME 类型选解析器 (PDF/DOCX/MD/TXT)
    │      → List<spring-ai Document>
    │
    ├─ Stage 2: SentenceAwareChunkingService.chunk()
    │   └─ 句子边界感知分块, 512 token, overlap 100
    │      → List<Chunk> (附带 spaceId/documentId metadata)
    │
    ├─ Stage 3+4: VectorStoreService.upsert()
    │   └─ 调用 EmbeddingModel (DashScope, 1536维)
    │      → 存入 Milvus collection "kb_chunks"
    │
    └─ Stage 5: ChunkMetadataService.save()
        └─ 写入 PostgreSQL document_chunks 表
           status → COMPLETED (或 FAILED)
```

## 问答请求流（标准 RAG）

```
POST /api/v1/spaces/{spaceId}/qa/ask/advanced
    │
    ▼ QnAController → QnAService.ask()
    │
    ├─ HybridSearchService.search()
    │   ├─ SemanticSearchService (Milvus 向量检索) ─┐ CompletableFuture 并行
    │   └─ KeywordSearchService (PG trgm 全文检索) ─┘
    │      RRF (k=60) 融合排序
    │
    ├─ RerankService.rerank() (DashScope gte-rerank, 可选)
    │
    ├─ ChatClient.prompt() → LLM 生成答案
    │
    └─ QaChatSessionService.saveExchange()
        ├─ 首次: 创建 qa_sessions 记录 (title=首条问题截断50字)
        └─ 后续: 追加 qa_messages + 刷新 updated_at
```

## 问答请求流（Agentic RAG）

```
POST /api/v1/spaces/{spaceId}/qa/ask
    │
    ▼ AgenticQnAService.ask()
    │
    └─ ReactAgent (Spring AI Alibaba)
        工具: searchKnowledgeBase(query, topK)
            └─ HybridSearch → Rerank → 返回 citations
        LLM 自主决定: 搜什么 · 搜几次 · 何时停止
        TokenBudgetService 控制最大 token 消耗
        会话历史: RedisChatMemory (key=sessionId)
```

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
