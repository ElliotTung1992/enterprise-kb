---
created: 2026-05-28
tags: [feature, evaluation, ragas, rag, llm-as-judge, ci-gate]
---

# Ragas RAG 评测框架接入

> 状态：**代码已落地、编译通过**（Java `RagasEvaluationServiceImpl` + Python `kb-ragas/`，对应 commit `7b1f0ad`）；运行态待联调（Python 服务连通性、metric 阈值门禁、CI gate 切换均需起栈实测）。
> 设计文档：`docs/design-ragas-integration.md`（含完整接口契约、phase 拆分、附录）
> 架构决策：[[decisions/adr-014-ragas-integration]]
> 评估目标：[[features/markdown-structure-rag]]（仅 `MdQnAService` + `MdAgenticQnAService`）

## 目标

给 md 结构感知 RAG 的两条链路打 4 个 Ragas 经典指标分，**补已退役的自研 trace 评估留下的 LLM-as-judge 缺口**（原规则断言 `mustContain` / `groundedOnly` 测不出语义级 faithfulness 和 relevancy）：

| 指标 | 测什么 | 需要 GT |
|------|--------|---------|
| `faithfulness` | 答案的每个事实陈述是否被 contexts 支持（**幻觉检测**） | 否 |
| `answer_relevancy` | 答案对原问题的语义覆盖度 | 否 |
| `context_precision` | 召回 chunk 与问题的相关度排序质量 | 否 |
| `context_recall` | 召回是否覆盖金标答案的关键信息 | **是** |

## 总体形态

```
EvalReplayService ─► MdQnAService / MdAgenticQnAService
        │              │
        │              ├─► MdParentExpansionService.expand() ──┐
        │              └─► searchKnowledgeBase / readFullSection ─┐
        │                                                        ▼
        │                                            RagasContextCollector (ThreadLocal)
        │                                                        │
        ▼                                                        │
   RagasEvaluationService ───── HTTP ─────► kb-ragas (Python/FastAPI)
        │                                          │
        │     ┌──── POST /evaluations              │
        │     └──── GET /evaluations/{jobId}       │
        │                                          ▼
        │                                  ragas.evaluate()
        ▼                                          │
   eval_runs / eval_run_results            judge LLM (Qwen-Max)
```

- **Java 侧**：复用 `eval_runs` / `eval_run_results` 三表（不新建 ragas 特异 schema），通过 `EvalRunController` 启动评估
- **Python 侧**：独立服务 `kb-ragas`，docker-compose `profile=eval` 按需起，FastAPI + ragas + langchain provider wrappers
- **接口契约**：HTTP path 用 `/evaluations`（去 Ragas 化命名），便于未来切 LangSmith / LangFuse / Phoenix 时只换 Python 实现

## 核心模式：RagasContextCollector

自研 trace 退役后，contexts（"答案依据文本"）改为 **运行时 ThreadLocal hook** 收集：

```java
public final class RagasContextCollector {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    public static Optional<Scope> current() { return Optional.ofNullable(CURRENT.get()); }

    public Scope openScope() {
        if (CURRENT.get() != null) throw new IllegalStateException("scope 已存在，不可嵌套");
        Scope scope = new Scope();
        CURRENT.set(scope);
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final Map<UUID, String> byParentId = new LinkedHashMap<>();
        public void recordParent(UUID id, String body) { byParentId.putIfAbsent(id, body); }
        public List<String> snapshot() { return List.copyOf(byParentId.values()); }
        @Override public void close() { CURRENT.remove(); }
    }
}
```

**接入位点**：

| 位点 | 写入时机 |
|------|---------|
| `MdParentExpansionService.expand()` 出口 | 单轮 RAG 拿到 expanded parents 后 `recordParents(list)` |
| `MdAgenticQnAService.searchKnowledgeBase` 工具 | 每次 child 检索后按 child→parent 映射 `recordParent` |
| `MdAgenticQnAService.readFullSection` 工具 | 读完整 section 后 `recordParent` |

业务代码只写一行 `RagasContextCollector.current().ifPresent(c -> c.recordParents(parents))`，**生产路径无 scope 时是 no-op、零开销**。`EvalReplayService` 用 `try-with-resources` 包裹评估、scope 关闭时取 `snapshot()`。

> 之所以不复用 trace step 表反查：自研 trace 已于 2026-05-28 经迁移 032 整体退役，[[log]] 有记录；外部 trace 平台（LangFuse via OTLP，见 [[features/langfuse-tracing]]）作为在线 tracing 接续，Ragas eval 与之统一为 v2 待办。

## 数据集

| dataset | 用途 | 来源 |
|---------|------|------|
| `regression` | CI 硬门禁，小而精（一期目标 MdQnA 30 题 + Agentic 20 题） | 人工标 `expected_json.referenceAnswer` |
| `synthetic-{spaceId}` | 趋势监控、不进 hard gate | Ragas `TestsetGenerator` 从 `md_parent_chunk` 合成 |
| `production-sample` | 预留位（二期） | 待外部 trace 平台接入 |

人工标注流：`qa_messages` 拾问题 → `POST /api/v1/admin/eval-cases` 写草稿 → `evals.html` 编辑器补 `referenceAnswer` → 两人交叉审核 → enable。

## Judge 配置

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `enterprise.kb.eval.ragas.judge-provider` | `DASHSCOPE` | 可在 `eval_runs.config_json.ragas` 覆盖 |
| `enterprise.kb.eval.ragas.judge-model` | `qwen-max` | 中文场景能力足够、成本可控 |
| `enterprise.kb.eval.ragas.embedding-provider` | `DASHSCOPE` | 与生产一致，避免分布偏移 |
| `enterprise.kb.eval.ragas.endpoint` | `http://kb-ragas:8000` | docker-compose 内网地址 |
| `enterprise.kb.eval.ragas.poll-interval-seconds` | `5` | Java 侧轮询 Python jobId |
| `enterprise.kb.eval.ragas.timeout-minutes` | `30` | 超时直接 FAILED |

不用同源 chat provider 做 judge 是避免 self-preference bias；境外闭源模型因中国区访问稳定性问题不作默认。

## 三阶段门禁

| 阶段 | 行为 | `RAGAS_GATE_MODE` |
|------|------|-------------------|
| Phase A（首月） | warn-only，记录基线，PR 不阻塞 | `warn` |
| Phase B（次月） | soft gate，跨阈值 fail PR 但允许 `[skip-ragas]` 跳过 | `soft` |
| Phase C（持续） | hard gate，跨阈值必须修复或显式降阈 | `hard` |

阶段切换不自动推进，由人工评审基线数据后切。

**阈值起点（Phase B/C 用）**：faithfulness ≥ 0.85（幻觉硬伤最高优先）/ answer_relevancy ≥ 0.80 / context_precision ≥ 0.70 / context_recall ≥ 0.75。

## 表 schema 复用

不动 DDL，只在三张已有表上加字段约定：

- `eval_cases.input_json` 新约定 `{question, spaceId, mode: SINGLE|AGENTIC, targetService}`
- `eval_cases.expected_json` 新增 `referenceAnswer` 字段
- `eval_runs.config_json` 新增 `ragas` 子树（judge/embedding/version/thresholds/gateMode）
- `eval_runs.summary_json` 新增 `ragasAverages` + `thresholdViolations`
- `eval_run_results.actual_json` 新增 `ragasScores` + `breakdown`（statements/verdicts/reasons 中间产物）+ `evaluationInput`

中间产物（statements/verdicts/reasons）落 actual_json：调试时不用重跑就能看到 "哪句被判幻觉"。

## 实施 Phase

| Phase | 内容 | 预估 |
|-------|------|------|
| P0 | Schema 与契约（写 ADR-014、字段约定、不动 DDL） | 0.5 天 |
| P1 | Python 服务 `kb-ragas/`（Dockerfile + FastAPI + ragas + provider wrappers） | 2-3 天 |
| P2 | Java `RagasEvaluationService` + `EvalRunServiceImpl` 加 RAGAS 分支 | 2 天 |
| P3 | `RagasContextCollector` + 接入 3 个写入点 + `EvalReplayService` 改造 | 2 天 |
| P4 | regression 数据集（人工标 50 题） + CI shell + GHA workflow（warn-only） | 3-5 天 |
| P5 | Python testset generator CLI 子命令 | 1-2 天 |
| P6 | `evals.html` 扩展（type=RAGAS 筛选 / breakdown 展开 / 从 qa_messages 拾题） | 2 天 |

总预估 **12-17 天单人**。

## 非目标

- 不接 LangSmith cloud（合规与稳定性）
- 不评 `CustomerAssistantService` / 投诉工作流（不依赖知识库检索，Ragas 4 指标不适用）
- 不做生产实时评估（避免线上 LLM 调用爆量）
- 不重新实现 Ragas 指标
- 不做自动调度（一期仅手动 + CI 触发）

## 关联

- 架构决策：[[decisions/adr-014-ragas-integration]]
- 评估对象：[[features/markdown-structure-rag]] · [[features/md-keyword-bm25]]
- 既有评估闭环：`eval_cases` / `eval_runs` / `eval_run_results` 三表（迁移 025；自研 trace 部分随迁移 032 退役，eval 部分保留）
- 数据库迁移：[[database/migrations]]（032 退役 trace 表 + `sourceTraceId` 列）
- AI Provider 体系：[[ai-rag/providers]]
