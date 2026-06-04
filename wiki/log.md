# Wiki Ingest Log

## 2026-06-04 | sync | BM25 build 跑通状态回灌
- Trigger: 用户确认 `.planning/未实现功能.md` 的「BM25 build 跑通」P0 项已实现（`db/manual/md-bm25-build.sql` 已执行，`tokenizer_catalog` 三表 + `md_model` 已生成，运行时走 BM25）。
- Pages updated: [[features/md-keyword-bm25]]（状态 banner + 实现状态 checklist 3·6 ✅ + 实测块加 2026-06-04 更新、旧块划线保留 + 配置表默认 BM25）、[[decisions/adr-013-md-keyword-bm25]]（状态 + 决策行默认）、[[features/markdown-structure-rag]]（关键词路 note）、[[Home]]（状态速览）、`hot.md`（新 ingest 条 + 旧 2026-05-27 条状态行）、`.planning/roadmaps/{未实现,已实现}功能.md`。
- grounding 订正：`application.yml:186` 实为 `keyword-mode: ${MD_KEYWORD_MODE:BM25}` → **生效默认早就是 BM25**（代码 `@Value(...:TRGM)` 只是属性缺失兜底）。wiki 多处旧记「默认仍 TRGM」系只看 `@Value` 未看 yml，本轮一并订正。
- Key insight: 状态从「代码已落地、build 未跑、降级 TRGM」翻为「build 跑通、运行走 BM25」。历史实测块（2026-05-27 三表 0 行）以删除线保留作时间线。

## 2026-06-04 | ingest | LangFuse 边界化重构架构文档
- Source: `docs/langfuse-tracing-architecture.md`（自 `todo.md` 第 3 点整理的 5 节重构设计）
- Summary: [[features/langfuse-tracing-boundary-refactor]]
- Pages created: [[features/langfuse-tracing-boundary-refactor]]
- Pages updated: [[features/langfuse-tracing]]（header 指针 + span 模型表 `[!stale]` + 并行传播 `[!stale]` + ① 传播表行 ② + I/O 写入规格旧 tool span 名）、[[decisions/adr-015-langfuse-tracing]]（note 修订 C2/C3/C4）、[[Home]]（功能行）、`hot.md`
- 核实动作：grep 核实 5 节方案**均已落地代码** —— `QaObservedAspect`/`@QaObserved`/`AiStreamTracingSupport` 存在且 `MdQnAController` 四端点已挂注解；`TracingSupport.root` 零命中；`retrievalExecutor`(`ContextPropagatingExecutor`) 在 `AppConfig` 且 `MdHybridSearchServiceImpl` 注入；`TracingToolInterceptor` 用 `DefaultToolCallingObservationConvention`/`spring.ai.tool` 且 `gen_ai.tool.execution` 零命中；`MdHybridSearchServiceImpl` 注释确认 `kb.retrieval.vector` 已去掉。
- Key insight: 源文档以「待重构实现」口吻写就，但**代码已走到目标态**——ingest 据代码实况记录，并把母页 [[features/langfuse-tracing]] 中被超越的早期描述（service 自建根 / `gen_ai.tool.execution` / 手写 `ContextSnapshot.wrapExecutor` / 手写 `kb.retrieval.vector`）逐处 `[!stale]` 标注指回新页。
- 范围克制：纯技术重构，无新增 entity/concept 页；连接全部内部（母页 / ADR-015 / md-structure-rag / providers）。

## 2026-06-04 | ingest | todo.md LangFuse 两子设计状态回灌
- Source: `todo.md`（项目根，TODO 状态清单）
- Summary: [[features/langfuse-tracing]] §Input/Output 映射 + §基础设施 span 噪音过滤
- Pages updated: [[features/langfuse-tracing]]（banner + IO 映射节状态 + 正文捕获脱敏节 + 新增噪音过滤节 + 待验证风险）、`hot.md`
- Pages created: 无（噪音过滤并入 [[features/langfuse-tracing]] 而非新页，沿用 2026-06-03 整合思路）
- 核实动作：grep 确认 `ChatModelContentObservationFilter` / `ExcludedObservationNamePredicate` / `TracingProperties.excludedObservationNamePrefixes` 类与 `application.yml` 黑名单、`ChatModelContentObservationFilterTest` 均存在 → todo "代码已落地"属实。
- Key insight: 两项从"Phase 1 待实现 / 未建页"推进到**代码落地、运行态待联调**；wiki 此前停在"待实现"，本轮把状态与实现细节同步。实测默认噪音黑名单（`tasks.scheduled.execution`/`http.server.requests`/`spring.security`/`spring.ai`）比 todo 列举更宽。
- 残留差：todo 引用的 `docs/design-langfuse-noise-filter.md` 设计文档实际不在 `docs/`（仅代码 + 本节记录），已在页内标注。

## 2026-06-03 | maintenance | wiki 结构整合（去冗余 -27%）
- Trigger: 审计发现结构冗余集中在视觉 RAG L2 历史集群（占全库 26%）+ 空目录 + 同主题碎片化。
- 整合动作（5811 → 4257 行，-1554 / -27%；54 → 52 文件）：
  - **视觉 RAG L2 集群 3 页 → 2 页**：`features/markdown-visual-rag-l2`(390) 重写为 ~55 行历史归档（吸收已删的 693 行沟通纪要）；`decisions/adr-011`(446) 压成 ~50 行决策墓碑；删除 `features/markdown-visual-rag-l2-design-notes.md`（693）。
  - **MD-RAG 验收并入**：`features/markdown-structure-rag-acceptance`(54) 折进 [[features/markdown-structure-rag]] §验收后删除。
  - **LangFuse io-mapping 并入**：`features/langfuse-io-mapping`(95) 压成紧凑节并入 [[features/langfuse-tracing]] §Input/Output 映射后删除（母体已 337 行，故压缩而非整篇搬入）。
  - 删空目录 `modules/`。
- 链接维护：所有指向被删页的入链已改指合并后页面 / §锚点（adr-011/012、Home、hot、log）；全库复扫无遗留 live 死链（log 内保留 `~~删除页~~` 删除线墓碑）。
- 未动：退役系统页面（adr-009/010 墓碑、document-ingestion / hybrid-search / agentic-qa 等）时效标记本就齐全；短 ADR、api/ 一控制器一页、feature/ADR 分离均为有意结构，不合并。

## 2026-06-03 | sync | docs/ 更新回灌（MiniMax 退役收尾）
- Trigger: `docs/` 多篇文档刚做了 MiniMax 清理 + 语句重写，回灌 wiki 的残留事实差。
- Pages updated:
  - [[decisions/adr-002-milvus-vector-store]] — 1536 维理由去掉 "MiniMax embo-01"
  - [[decisions/adr-008-intent-routing-two-tier]] — 路由廉价模型示例 MiniMax → 默认本地 `LLAMA_CPP`
  - [[decisions/adr-014-ragas-integration]] — judge 否决项去掉 MiniMax，理由改挂"默认 chat 为 llama.cpp、judge 与生产不同源"
  - `log.md`（2026-05-31 条）— 命门 insight 里 `minimax OpenAiChatModel.builder()` 笔误订正为 `llama.cpp`
- Key insight: wiki 在 MiniMax 退役上**已基本同步**（`ai-rag/providers.md` 早有退役墓碑、Home/tech-stack/providers 均已标 llama.cpp 为默认 chat），本轮只补三处 ADR 历史理据 + 一处日志笔误。md Agentic 在 wiki 无矛盾（[[features/markdown-structure-rag]] 已记为已实现），docs 的散文重写不涉及 wiki 事实，未动。
- 保留：`ai-rag/providers.md` 的 MiniMax 退役墓碑（有意保留，作"勿再引入"记录）。

## 2026-06-02 | ingest | LangFuse Input/Output 映射设计
- Source: `docs/design-langfuse-io-mapping.md`（+ `docs/design-langfuse-tracing.md` 已由现有页覆盖，仅交叉引用）
- Summary: [[features/langfuse-tracing]] §Input/Output 映射（原独立页 2026-06-03 已并入）
- Pages created: ~~features/langfuse-io-mapping~~（2026-06-03 整合并入 [[features/langfuse-tracing]] §Input/Output 映射）
- Pages updated: [[features/langfuse-tracing]]（加子设计链接 + event/attribute 待验证项补注）、[[Home]]（功能行加新页）
- Key insight: io-mapping 是 langfuse-tracing 落地后的补齐子设计——现状是 trace 结构有了但 `observations.input/output` 全 NULL，只知"调用发生"不知"问了/答了什么"。方案两阶段：Phase 1 给项目自建业务 span（`kb.qa.ask*` / `kb.retrieval.*` / `gen_ai.tool.execution` / `kb.ingest.*`）经 `TracingSupport` 直写 `langfuse.observation.input/output`（脱敏收口在 TracingSupport，绕开 Spring AI event/attribute 不确定性）；Phase 2 才用 `ObservationHandler<ChatModelObservationContext>` PoC 评估自动 generation span。embedding span 明确不写 input/output。
- 注：`design-langfuse-tracing.md` 内容已由 [[features/langfuse-tracing]] + [[decisions/adr-015-langfuse-tracing]] 完整覆盖，本轮未重复建页。

## 2026-05-31 | maintenance | API 文档覆盖 + tech-stack 同步
- Type: lint / coverage
- Trigger: 二次盘点发现 11 个 controller 仅 5 个有 api 文档，且 search 端点早已删除
- Pages deleted:
  - `wiki/api/search.md`（无对应 `SearchController`，端点不存在）
- Pages created:
  - [[api/spaces]] · [[api/users]] · [[api/eval]] · [[api/complaints]] — 补齐 SpaceController / UserController / EvalRun + EvalCaseController / ComplaintController 的端点说明
- Pages rewritten:
  - [[architecture/tech-stack]] — 删 OpenAI provider，补 LangFuse / ClickHouse / Ragas 依赖，PG 镜像改 vchord-suite，区分默认 profile 与 `--profile tracing`
  - [[infrastructure/docker-compose]] — 补 `--profile tracing` 启动 + 必填密钥；服务依赖图区分默认 / tracing profile
  - [[database/entities/users-spaces]] — EDITOR 权限删"管理标签"（已随 `tags` 表退役）
- Home.md：API 接口行改为 `auth` / `users` / `spaces` / `documents` / `qa` / `after-sales` / `complaints` / `eval`（删 search，加 4 个新页）
- Key insight: 11 个活跃 controller — `AuthController`、`UserController`、`SpaceController`、`MdDocumentController`、`MdQnAController`、`QnAController`（session 端）、`CustomerAssistantController`、`ReviewController`（空间 scope）、`ComplaintController`、`EvalRunController`、`EvalCaseController`。审核员决策有两个入口：`/after-sales/reviews/*`（全局）和 `/spaces/{spaceId}/reviews/*`（空间内），前者已记 [[api/after-sales]]，后者通过 [[features/hitl-after-sales]] 间接覆盖。

## 2026-05-31 | maintenance | 全 wiki 对齐标准 RAG 退役 + 文档目录整理
- Type: lint / cleanup
- Trigger: 整理 .planning/ docs/ wiki/ 三块文档时发现 25+ 页含已退役概念（标准 RAG 竖井 / `kb_chunks` / `kb-knowledge-graph` / `/qa/ask` 旧端点）
- Pages deleted:
  - `wiki/features/knowledge-graph.md`（描述已删 `AutoTaggingService` / `DocumentRelationService` / `TagController`）
  - `wiki/database/entities/tags-graph.md`（描述已 drop 的 `tags` / `document_relations`）
  - `wiki/database/entities/documents-chunks.md`（描述已 drop 的 `documents` / `document_chunks`）
- Pages created:
  - [[decisions/adr-009-agent-trace-foundation]] / [[decisions/adr-010-agent-trace-eval]] — 自研 trace 体系 tombstone（迁移 032 下线，接续 ADR-014 / ADR-015）
  - [[features/markdown-image-rag]] — 补齐 `docs/design-md-image-rag.md` 对应 feature 页
- Pages rewritten (md-only 现状)：
  - [[api/qa]] · [[api/documents]] · [[ai-rag/agentic-qa]] · [[ai-rag/hybrid-search]] · [[ai-rag/providers]] · [[ai-rag/session-memory]]
  - [[architecture/overview]] · [[architecture/module-dependency]] · [[architecture/data-flow]]
  - [[database/schema-overview]] · [[database/migrations]]
  - [[features/document-ingestion]] · [[features/hitl-after-sales]] · [[infrastructure/services]]
- ADR 修订：
  - ADR-001 ~ 004 补 frontmatter + 标注退役范围（`kb-knowledge-graph` / `kb_chunks` / `HybridSearchService` / `AgenticQnAServiceImpl` 各自接续到 md 竖井）
  - ADR-011 状态由"已接受（待实施）"改为"Phase 5 实现，理念并入 md 图片 RAG"
- Home.md：补 [[api/auth]] / ADR-002 / 003 / 004 / 009 / 010 / [[features/markdown-image-rag]]；删 `modules/kb-*` 死链（modules/ 目录从不存在）
- Key insight: 标准 RAG 竖井（`documents` / `document_chunks` / `kb_chunks` / `QnAService` / `HybridSearchService` / `kb-knowledge-graph` 模块）随迁移 031、自研 trace 随迁移 032 整体退役。当前唯一活跃 RAG 竖井 = **Markdown 结构感知 RAG**（small-to-big 父子索引，`md_documents` / `md_parent_chunk` / `md_child_chunk` / `md_document_assets` + Milvus `md_kb_chunks`）。剩余 ADR/历史 acceptance/design-notes 中的 `kb_chunks` 引用是合法的历史上下文（已在状态行标记），保留不改写。

## 2026-05-31 | save | LangFuse 在线 LLM Tracing 设计
- Type: feature
- Source: `docs/design-langfuse-tracing.md`（已落地编译通过、运行态未验证）
- Location: `wiki/features/langfuse-tracing.md`
- Pages created:
  - [[features/langfuse-tracing]]
- Pages updated:
  - [[Home]] — 功能导航补 `langfuse-tracing` / `ragas-evaluation`，架构决策补 ADR-014/015
- Key insight: 厂商中立 OTLP 管线 + 自部署 LangFuse + service 入口自建唯一业务根 span。**ChatModel/VectorStore 的 ObservationRegistry 是命门**——llama.cpp `OpenAiChatModel.builder()` 和手工 `mdVectorStore` 不注入就落 NOOP，默认链路零 trace。跨线程传播必须 ①+②+③ 同批做：reactor 自动传播 + `ContextSnapshot` 包 `MdHybridSearchService` 并行检索 executor + 关 `DashScopeAsyncToolCallingManager`，否则 retrieval/LLM span 脱根成孤儿 trace。Tool span 用框架原生 `ToolInterceptor`（业务工具体零侵入），比"在 lambda 体内手写 span"或旧 `TraceToolInterceptor` 切入点更标准。业务上下文（`langfuse.user.id` / `langfuse.session.id` / metadata）走本地 thread-local + `SpanProcessor` 复制到子 span，**不经 baggage**——避免随下游 HTTP 请求外发给模型 provider。

## 2026-05-27 | ingest | MD 关键词检索升级 BM25 设计
- Source: `docs/design-md-keyword-bm25.md`（+ 本会话对运行库的实测）
- Summary: [[features/md-keyword-bm25]]
- Pages created:
  - [[features/md-keyword-bm25]]
  - [[decisions/adr-013-md-keyword-bm25]]
- Pages updated:
  - [[Home]] — 功能导航 + 架构决策导航
  - [[features/markdown-structure-rag]] — 关键词路 BM25 升级交叉引用
  - [[database/migrations]] — 新增迁移 029 + 运维脚本备注
  - [[ai-rag/hybrid-search]] — 与 md 竖井 BM25 变体的关系
- Key insight: md 竖井关键词路从 pg_trgm（无 IDF/长度归一/词级匹配）升级为真正 BM25（VectorChord-bm25 + pg_tokenizer jieba），**改动只收敛到 `MdKeywordSearchServiceImpl` 一个 method**——因为 RRF 按排名融合、对分数符号无感，BM25 负分无需归一化。三件套 `md_ta`(jieba 切词) → `md_model`(词→ID 冻结词表) → `md_tok`；`bm25vector` 存每篇 TF（`{id:值}` 严格升序），DF/IDF 在 `USING bm25` 倒排索引、查询时 `to_bm25query` 现算。实测：029 已 apply 但 build 脚本未跑（`tokenizer_catalog` 三表 0 行），BM25 链路尚未真正可用；默认仍 TRGM。

## 2026-05-26 | ingest | Markdown 结构感知 RAG 验收文档
- Source: `docs/acceptance-md-structure-rag.md`
- Summary: [[features/markdown-structure-rag]]（§验收，原独立验收页 2026-06-03 已并入）
- Pages created:
  - [[features/markdown-structure-rag]]
  - ~~features/markdown-structure-rag-acceptance~~（2026-06-03 整合并入 [[features/markdown-structure-rag]] §验收）
  - [[decisions/adr-012-markdown-structure-rag]]（ingest 后补建：提炼 C1–C17 核心架构决策）
- Pages updated:
  - [[Home]] — 功能导航 + 架构决策导航
  - [[database/migrations]] — 新增迁移 027/028
- Key insight: 「结构感知 RAG（small-to-big 父子索引）」是与「图文 RAG L2」**不同的另一条平行竖井**（`md_documents`/`md_kb_chunks`，标准竖井零改动）；child 边界只决定召回命中，回答返回完整 parent，故弃用标准链 100-token overlap；融合阶段不去重以保留多 child 位置供多窗节选。

## 2026-05-18 | save | 客服助手意图识别 — 两层域路由
- Type: feature
- Location: wiki/features/intent-routing.md
- From: grill-me 设计评审 → ADR-008 → 分阶段实施计划 → Phase 0–3 全部实现
- Pages created:
  - [[features/intent-routing]]
- Pages updated:
  - [[Home]] — 功能导航新增意图路由
- Key insight: 意图识别准确率拆成"域路由 vs 域内工具"两层独立度量；few-shot 与冻结测试集必须硬切分防污染；`routing-enabled` kill-switch 默认 false，先影子后灰度。

## 2026-05-13 | ingest | HITL 售后审核 & 商城客服助手
- Source: `.planning/2026-05-13-hitl-after-sales/` (task_plan.md + findings.md + progress.md)
- Summary: [[features/hitl-after-sales]]
- Pages created:
  - [[features/hitl-after-sales]]
  - [[ai-rag/hitl-hook]]
  - [[api/after-sales]]
  - [[database/entities/after-sales-tables]]
  - [[decisions/adr-005-hitl-transaction-ordering]]
  - [[decisions/adr-006-customer-assistant-separation]]
- Pages updated:
  - [[Home]] — 新增功能、AI/RAG、API、架构决策导航
  - [[database/schema-overview]] — 新增迁移 018-020 和售后表实体链接
  - [[ai-rag/agentic-qa]] — 标注 HITL 已从此服务移除
- Key insight: HITL 审批必须先调用 LLM（resumeWithFeedback）再提交 DB，否则 LLM 失败后申请状态已变无法重试。
