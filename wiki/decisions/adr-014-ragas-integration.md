---
created: 2026-05-28
tags: [adr, evaluation, ragas, rag, llm-as-judge, ci-gate, langsmith]
---

# ADR-014：Ragas RAG 评测框架接入

**状态**：代码已落地、编译通过；运行态待联调（Python Ragas 服务连通性、`eval_runs` 写回、metric 阈值门禁均需起栈实测）

> 详细设计见 [[features/ragas-evaluation]]。完整推演与接口契约见 `docs/design-ragas-integration.md`。本 ADR 是 [[decisions/adr-012-markdown-structure-rag]] 与 [[decisions/adr-013-md-keyword-bm25]] 的评估侧后续——给 md 竖井加 LLM-as-judge 答案质量门禁。

## 背景

自研 trace 体系（原 ADR-009 / ADR-010 描述的 `agent_traces` / `agent_trace_steps` + `TraceRecorder` + `TraceFacade`）于 2026-05-28 通过迁移 032 全部退役（[[log]] 有完整记录）。退役后保留的评估基础：

- `eval_cases` / `eval_runs` / `eval_run_results` 三表（迁移 025 创建，032 拆掉 `source_trace_id` FK 与列）
- 两个上线的离线 eval：`MdKeywordEvalTest`（TRGM vs BM25 召回）、`DomainRouterEvalTest`（域路由准确率）
- `EvalReplayService`（trace replay 能力随迁移 032 退役，剩按 `eval_cases.input_json` 跑目标 RAG 服务的最小静态断言）
- 后台页 `evals.html`

缺口：**答案质量**只有规则断言（`mustContain` / `mustNotContain` / `groundedOnly`），测不到语义级 faithfulness、relevancy、context precision/recall。这些都需要 LLM-as-judge。

## 决策

接入 [Ragas](https://github.com/explodinggradients/ragas) 作为 LLM-as-judge 的实现，落地为**独立 Python 服务 + Java 调 HTTP** 形态；**复用** `eval_runs` / `eval_run_results` 三表，不新建 ragas 特异 schema。

## 关键决策

### 1. 不接 LangSmith Cloud，走本地 Python 服务（C1）

否决：LangSmith cloud 在中国区访问不稳定，自部署 Enterprise tier 价格 ~$3K/月起；Ragas 是 LangSmith 内含的子集能力，本地化更可控。**接口契约设计成通用 evaluation 命名**（`/evaluations`、`judgeProvider` 等），未来如改接 LangSmith / LangFuse / Phoenix，**只替换 Python 服务实现，不动 Java 调用契约**。

### 2. 指标范围限 4 个经典 Ragas 指标（C2）

`faithfulness` + `answer_relevancy` + `context_precision` + `context_recall`。否决加 `answer_correctness`（加权合成调参贵）、自定义业务指标（一期 MVP 外）。**仅评 `MdQnAService` + `MdAgenticQnAService` 两条 RAG 链路**，不评 `CustomerAssistantService` / 投诉工作流（不依赖知识库检索，Ragas 4 指标不适用）。

### 3. 复用 eval_runs / eval_run_results 表，不新建 ragas 特异 schema（C3）

`config_json.ragas` 子树 + `summary_json.ragasAverages` + `actual_json.ragasScores` + `actual_json.breakdown` 全部按 jsonb 字段约定，不动 DDL。后台 `evals.html`、查询、CI 退出码逻辑全复用。**如果接 LangSmith 等替代实现，同一套 eval_runs 行能容纳多种 evaluator 类型**——这是把 ragas 当"一种 eval runner"而不是"独立子系统"的根本。

### 4. contexts 收集：运行时 ThreadLocal hook，不依赖任何持久化 trace（C4）

自研 trace 退役后，原计划"从 `agent_trace_steps` 反查 tool output_json" 整条路径失效。新方案 `RagasContextCollector`：

- ThreadLocal 持有当次评估的 Scope，业务代码以 `current().ifPresent(c -> c.recordParents(...))` 写入
- **生产请求路径无 scope 时是 no-op、零开销**——hook 只在 `EvalReplayService` 通过 `try-with-resources` 显式 `openScope()` 时激活
- 接入 3 个写入点：`MdParentExpansionService.expand()` 出口、`searchKnowledgeBase` 工具、`readFullSection` 工具
- 单轮 RAG 和 Agentic **共用同一收集器接口**，避免两路实现

**约束**：ThreadLocal 不跨虚拟线程传播，Agent 跑在调用线程上够用；如未来 `ReactAgent` 切异步执行，要换成可显式传播的 scope 实现。

### 5. Judge 默认 DashScope Qwen-Max（C5）

否决用 Anthropic 作 judge（中国区网络不稳）。选 Qwen-Max 的理由：DashScope 是默认 embedding 提供商，但不是默认 chat（默认 chat 为本地 llama.cpp），所以 judge 与生产 chat 不同源，self-preference bias 可接受；同时中文能力足够、调用稳定，API key 还能复用 `DASHSCOPE_API_KEY`。embedding 维持 DashScope `text-embedding-v3`，与生产一致避免分布偏移。

### 6. 数据集：regression + synthetic，production-sample 推迟（C6）

- `regression`：人工标注小集（MdQnA 30 + Agentic 20），用于 CI 门禁
- `synthetic-{spaceId}`：Ragas `TestsetGenerator` 从 `md_parent_chunk` 自动合成，仅用于趋势监控，**永远不进 hard gate**（合成 GT 是 LLM 生成的）
- `production-sample`：原计划从 `agent_traces` 抽样，自研 trace 退役后推迟到外部 trace 平台接入后重新设计

### 7. 三阶段门禁，环境变量切换（C7）

```
Phase A (1 月) warn-only → Phase B (1 月) soft gate → Phase C (持续) hard gate
                通过 RAGAS_GATE_MODE=warn|soft|hard 切
```

阶段切换由人工评审基线数据后切，不自动推进。阈值起点 faithfulness ≥ 0.85（幻觉是硬伤）/ answer_relevancy ≥ 0.80 / context_precision ≥ 0.70 / context_recall ≥ 0.75，可在 `application.yml` 配置、`eval_runs.config_json.ragas.thresholds` 覆盖。

### 8. 部署形态：docker-compose profile=eval（C8）

`kb-ragas` 服务挂 `profile: [eval]`，平时不起、占资源 0；评估时 `docker compose --profile eval up -d kb-ragas`，启动前 Java 侧 `EvalRunService` 探活 `/health`，没起 fail-fast。本地、CI、prod 一致。

### 9. 交互：批量提交 + 5s 轮询，job state 内存存（C9）

否决 webhook（要暴内部 endpoint、加鉴权）、逐题同步 HTTP（一次 30+ 分钟评估 timeout）、SSE（过重）。Python BackgroundTasks 异步跑、内存存 jobId 状态；进程 crash 接受丢 job state（重跑即可），二期视需要换 Redis 持久化。

### 10. 中间产物全落 actual_json（C10）

`statements` / `verdicts` / `reasons` / `generatedQuestions` / `perContextVerdict` 等 Ragas breakdown 数据全部存进 `eval_run_results.actual_json.breakdown`，单题 1-5KB，100 题 dataset 约 500KB，PG 能扛。**调试时不用重跑就能看到"哪句话被判幻觉"**——LLM judge non-determinism 会让重跑结果不一致，必须留证据。

## 非目标

- 不接 LangSmith Cloud（合规、稳定性、价格）
- 不评 `CustomerAssistantService` / 投诉工作流（不依赖知识库检索）
- 不做生产实时评估（每次问答不触发 Ragas）
- 不重新实现 Ragas 指标（不在 Java 侧造轮子）
- 不做自动调度（一期仅手动 + CI 触发）
- 不重启 `production-sample` 数据源（trace 退役后推迟二期）

## 影响

**正面**：

- 补已退役自研 trace 评估能力留下的 LLM-as-judge 缺口，语义级 faithfulness / relevancy / context 质量可量化
- 通用 evaluation 接口契约让未来切 LangSmith / LangFuse / Phoenix 时 Java 零改动
- 表复用，schema 增量 = 0，后台页面与 CI 退出码逻辑全部沿用
- `RagasContextCollector` ThreadLocal 模式 hook 化业务代码侵入到一行

**成本**：

- 引入 Python 服务（新增运维面）：4 个容器之外的第 5 个，但走 `profile=eval` 平时不起
- judge LLM 调用费：100 题 × 4 指标 × 4-10 次/指标 ≈ 数千次 LLM 调用/次评估，需账单审计
- 人工标注 GT 周期长：regression 小集 50 题需两人交叉审核
- 自研 trace 退役后 `production-sample` 数据源断流，**生产侧持续监控能力暂停**

## 备选方案

- **直接接 LangSmith Cloud**：合规阻塞（中国区数据出境）、网络不稳、价格 → 否决（见 C1）
- **LangFuse 自部署**：可行替代，UI 接近 LangSmith、合规、免费；但一期不上独立 trace/eval 平台，先把 Ragas 跑通再讨论 → 推迟二期（[[features/ragas-evaluation]] §"非目标"）
- **Java 侧自写 LLM-as-judge prompt**：成本高、与社区指标语义不可比、prompt 漂移难维护 → 否决
- **维持规则断言（无 LLM judge）**：测不到语义级幻觉与相关度，正是本 ADR 要解决的问题

## 关联

- 功能方案：[[features/ragas-evaluation]]
- 评估目标：[[decisions/adr-012-markdown-structure-rag]] · [[features/markdown-structure-rag]] · [[features/md-keyword-bm25]]
- 前置：原 ADR-009 / ADR-010（自研 trace 体系）已于 2026-05-28 退役（迁移 032），eval 三表保留
- AI Provider 体系：[[ai-rag/providers]]
- 数据库迁移：[[database/migrations]]
