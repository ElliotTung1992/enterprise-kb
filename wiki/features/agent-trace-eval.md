---
created: 2026-05-18
tags: [feature, observability, agent-trace, replay, eval]
---

# 功能：Agent Trace、离线复现包与评估回放

## 概述

本功能为 Agentic RAG、商城客服助手、售后 HITL、投诉升级提供统一运行轨迹采集能力。目标是让一次线上对话可以被查清楚、导出、离线复现，并沉淀为评估用例。

架构决策见 [[decisions/adr-009-agent-trace-replay-eval]]。

## 成功标准

- 每次 Agentic QA / 客服助手对话至少生成一条 `agent_traces`。
- 每次工具调用都有 step 记录，包含工具名、入参、出参、异常、耗时。
- HITL 中断能在 trace 详情中看到 tool_call_id、工具参数、review request 关联。
- 能按 traceId 导出 replay JSON。
- 能从 trace 生成 eval case 草稿，并支持 JSONL 导入/导出。
- 能跑三类评估：路由/工具选择、检索质量、答案质量。
- trace 后台只允许管理员访问。

## 数据流

```mermaid
flowchart TD
    A["用户请求 / resume"] --> B["业务入口<br/>AgenticQnAService / QnAService / CustomerAssistantService"]
    B --> C["TraceRecorder.startTrace<br/>同步创建 agent_traces"]

    C --> D{"执行链路类型"}
    D --> E["普通 RAG<br/>rewrite / HyDE / retrieval / rerank / answer"]
    D --> F["Agentic RAG<br/>ReactAgent + searchKnowledgeBase"]
    D --> G["客服助手<br/>guard / router / domain handler"]
    D --> H["HITL resume<br/>恢复售后 Agent"]

    E --> I["recordStep<br/>MODEL_CALL / RETRIEVAL / RERANK"]
    F --> J["recordStep<br/>MODEL_CALL / TOOL_CALL"]
    G --> K["recordStep<br/>GUARD / ROUTER / TOOL_CALL"]
    H --> L["recordStep<br/>RESUME / MODEL_CALL"]

    J --> M{"是否 HITL 中断"}
    K --> M
    L --> M
    M -- "是" --> N["recordHitlInterrupt<br/>同步写关键 step"]
    M -- "否" --> O["业务响应"]
    N --> O
    I --> O

    O --> P{"成功还是异常"}
    P -- "成功" --> Q["completeTrace<br/>同步更新输出和耗时"]
    P -- "异常" --> R["failTrace<br/>同步更新错误摘要"]

    I -. "异步 best-effort" .-> S[("agent_trace_steps")]
    J -. "异步 best-effort" .-> S
    K -. "异步 best-effort" .-> S
    L -. "异步 best-effort" .-> S
    N --> S
    C --> T[("agent_traces")]
    Q --> T
    R --> T
```

## 设计图

### 总体架构

```mermaid
flowchart LR
    subgraph Runtime["运行期链路"]
        A1["Agentic Q&A"]
        A2["普通 RAG"]
        A3["客服助手"]
        A4["售后域 Agent"]
        A5["投诉域 Agent"]
    end

    subgraph Recorder["Trace 采集层"]
        B1["TraceRecorder"]
        B2["traceTool wrapper"]
        B3["TraceRedactionService"]
        B4["traceExecutor<br/>异步 step 写入"]
    end

    subgraph Storage["PostgreSQL"]
        C1[("agent_traces")]
        C2[("agent_trace_steps")]
        C3[("eval_cases")]
        C4[("eval_runs")]
        C5[("eval_run_results")]
    end

    subgraph Admin["后台与离线能力"]
        D1["Trace Admin API"]
        D2["traces.html"]
        D3["Replay JSON Export"]
        D4["Eval API"]
        D5["evals.html"]
        D6["EvalReplayService"]
        D7["JSONL Import / Export"]
    end

    A1 --> B1
    A2 --> B1
    A3 --> B1
    A4 --> B1
    A5 --> B1
    B1 --> B2
    B1 --> B3
    B1 --> B4
    B1 --> C1
    B4 --> C2
    D1 --> C1
    D1 --> C2
    D2 --> D1
    D1 --> D3
    D3 --> D6
    D4 --> C3
    D4 --> C4
    D4 --> C5
    D5 --> D4
    D6 --> C3
    D6 --> C4
    D6 --> C5
    D7 --> C3
```

### 数据模型

```mermaid
erDiagram
    agent_traces ||--o{ agent_trace_steps : contains
    agent_traces ||--o{ eval_cases : seeds
    eval_cases ||--o{ eval_run_results : evaluated_by
    eval_runs ||--o{ eval_run_results : contains

    agent_traces {
        uuid id PK
        varchar trace_type
        uuid session_id
        uuid space_id
        uuid user_id
        varchar request_id
        varchar model_provider
        varchar status
        text input_text
        text output_text
        jsonb raw_input_json
        jsonb raw_output_json
        bigint duration_ms
        integer tokens_used
        varchar error_type
        text error_message
        boolean payload_truncated
        timestamptz started_at
        timestamptz completed_at
        timestamptz created_at
    }

    agent_trace_steps {
        uuid id PK
        uuid trace_id FK
        integer step_index
        varchar step_type
        varchar agent_name
        varchar tool_call_id
        varchar tool_name
        varchar status
        jsonb input_json
        jsonb output_json
        jsonb raw_payload_json
        varchar business_ref_type
        uuid business_ref_id
        bigint duration_ms
        varchar error_type
        text error_message
        boolean payload_truncated
        timestamptz created_at
    }

    eval_cases {
        uuid id PK
        uuid source_trace_id FK
        varchar case_type
        varchar name
        varchar dataset
        jsonb input_json
        jsonb expected_json
        jsonb mock_outputs_json
        boolean enabled
        uuid created_by
        timestamptz created_at
        timestamptz updated_at
    }

    eval_runs {
        uuid id PK
        varchar dataset
        varchar status
        jsonb config_json
        jsonb summary_json
        timestamptz started_at
        timestamptz completed_at
        timestamptz created_at
    }

    eval_run_results {
        uuid id PK
        uuid eval_run_id FK
        uuid eval_case_id FK
        varchar status
        jsonb actual_json
        jsonb assertion_result_json
        text failure_reason
        timestamptz created_at
    }
```

### 复现与评估回放

```mermaid
sequenceDiagram
    autonumber
    participant Admin as 管理员
    participant UI as Trace/Eval 页面
    participant TraceAPI as Trace Admin API
    participant TraceDB as agent_traces / steps
    participant EvalAPI as Eval API
    participant EvalSvc as EvalReplayService
    participant EvalDB as eval_cases / runs / results
    participant Mock as Mock Tool Outputs

    Admin->>UI: 查看 trace 详情
    UI->>TraceAPI: GET /api/v1/admin/traces/{traceId}
    TraceAPI->>TraceDB: 读取 trace + steps
    TraceDB-->>TraceAPI: 返回完整轨迹
    TraceAPI-->>UI: 展示 prompt / tools / raw JSON

    Admin->>UI: 导出 replay JSON
    UI->>TraceAPI: GET /api/v1/admin/traces/{traceId}/replay
    TraceAPI->>TraceDB: 组装 replay package
    TraceAPI-->>UI: replay JSON

    Admin->>UI: 生成 eval case 草稿
    UI->>TraceAPI: POST /api/v1/admin/traces/{traceId}/eval-case
    TraceAPI->>EvalDB: 写入 eval_cases 草稿
    TraceAPI-->>UI: 返回 caseId

    Admin->>UI: 启动评估
    UI->>EvalAPI: POST /api/v1/admin/eval-runs
    EvalAPI->>EvalSvc: run(dataset)
    EvalSvc->>EvalDB: 读取 eval_cases
    EvalSvc->>Mock: 副作用工具使用 mock_outputs_json
    Mock-->>EvalSvc: 返回历史工具输出
    EvalSvc->>EvalDB: 写入 eval_runs / eval_run_results
    EvalAPI-->>UI: 返回评估汇总
```

## 第一版接入点

| 链路 | 文件 | 接入内容 |
|------|------|----------|
| Agentic RAG | `AgenticQnAServiceImpl` | trace 外壳、system prompt/history/user、`searchKnowledgeBase` 工具、检索/rerank/citations、最终答案 |
| 普通 RAG | `QnAServiceImpl` | query rewrite、HyDE、hybrid search candidates、rerank topK、prompt、答案、token usage |
| 客服旧单体路径 | `CustomerAssistantServiceImpl.legacyChat()` | trace 外壳、影子路由、3 个工具、HITL 中断、最终答案 |
| 客服两层路由 | `CustomerAssistantServiceImpl.routedChat()` | guard、domain route、dispatch domain、最终答案、domain |
| 售后域 | `AfterSalesDomainHandler` | `checkAfterSalesEligibility`、`submitAfterSalesReview` HITL 中断、resume |
| 投诉域 | `ComplaintDomainHandler` | `escalateComplaint` 工具、complaintId、plan 启动结果 |
| 投诉执行器 | `ComplaintExecutorServiceImpl` | 后续阶段接入通知、赔付、结案等工具 |

## 模块设计

### DTO / Model

建议放在 `kb-search`：

- `model/AgentTrace.java`
- `model/AgentTraceStep.java`
- `model/EvalCase.java`
- `model/EvalRun.java`
- `model/EvalRunResult.java`
- `dto/TraceStartRequest.java`
- `dto/TraceStepRequest.java`
- `dto/TraceReplayPackage.java`
- `dto/EvalCaseDto.java`
- `dto/EvalRunDto.java`

### Service

| 服务 | 职责 |
|------|------|
| `TraceRecorder` | Agent 入口和工具回调使用的轻量记录器 |
| `AgentTraceService` | trace 查询、详情、导出 replay、生成 eval 草稿 |
| `EvalCaseService` | eval case CRUD、JSONL 导入导出 |
| `EvalReplayService` | 读取 eval case/replay package 并执行回放 |
| `TraceRedactionService` | 后台展示和导出时遮挡敏感 key |

`TraceRecorder` 应足够轻，不在业务路径里做复杂查询。复杂导出和评估放到 `AgentTraceService` / `EvalReplayService`。

### Mapper

MyBatis XML：

- `AgentTraceMapper.xml`
- `AgentTraceStepMapper.xml`
- `EvalCaseMapper.xml`
- `EvalRunMapper.xml`
- `EvalRunResultMapper.xml`

所有查询默认按 `deleted_at IS NULL` 或 status/时间范围过滤；trace 第一版可不做软删除，依靠保留周期清理。

## API 草案

后台 API 建议放在 `/api/v1/admin/traces` 和 `/api/v1/admin/evals`。

### Trace

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/admin/traces` | trace 列表，支持时间、类型、sessionId、spaceId、status、tool、error 过滤 |
| `GET` | `/api/v1/admin/traces/{traceId}` | trace 详情，含 steps |
| `GET` | `/api/v1/admin/traces/{traceId}/replay` | 导出 replay JSON |
| `POST` | `/api/v1/admin/traces/{traceId}/eval-case` | 从 trace 生成 eval case 草稿 |
| `DELETE` | `/api/v1/admin/traces/{traceId}` | 可选：删除单条 trace |

权限：`ROLE_SYSTEM_ADMIN` 或空间 `ADMIN`。空间 `ADMIN` 只能查本空间数据；客服全局 trace 若 `spaceId` 为空，只允许系统管理员。

### Eval

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/admin/eval-cases` | eval case 列表 |
| `GET` | `/api/v1/admin/eval-cases/{id}` | eval case 详情 |
| `POST` | `/api/v1/admin/eval-cases` | 创建 eval case |
| `PUT` | `/api/v1/admin/eval-cases/{id}` | 更新 eval case |
| `POST` | `/api/v1/admin/eval-cases/import-jsonl` | 导入 JSONL |
| `GET` | `/api/v1/admin/eval-cases/export-jsonl` | 导出 JSONL |
| `POST` | `/api/v1/admin/eval-runs` | 启动一次评估 |
| `GET` | `/api/v1/admin/eval-runs/{id}` | 查看评估汇总和逐 case 结果 |

## Replay JSON 草案

```json
{
  "traceId": "uuid",
  "traceType": "AGENTIC_QA",
  "sessionId": "uuid",
  "spaceId": "uuid",
  "userId": "uuid",
  "modelProvider": "DASHSCOPE",
  "input": {
    "message": "用户问题",
    "history": [],
    "systemPrompt": "..."
  },
  "tools": [
    {
      "name": "searchKnowledgeBase",
      "description": "...",
      "inputSchema": "KnowledgeSearchInput"
    }
  ],
  "steps": [
    {
      "index": 1,
      "type": "TOOL_CALL",
      "toolName": "searchKnowledgeBase",
      "input": {"query": "退款规则"},
      "output": {"text": "...", "hits": []},
      "durationMs": 123
    }
  ],
  "finalOutput": {
    "answer": "...",
    "citations": []
  }
}
```

## Eval Case JSONL 草案

每行一个 case：

```json
{"id":"case-001","caseType":"ROUTING_TOOL","dataset":"smoke","input":{"message":"我要投诉商家一直不处理退款","history":[]},"expected":{"domain":"COMPLAINT","requiredTools":["escalateComplaint"],"forbiddenTools":["submitAfterSalesReview"]}}
```

检索质量 case：

```json
{"id":"case-002","caseType":"RETRIEVAL","dataset":"frozen","input":{"message":"退货超过7天还能申请吗","spaceId":"..."},"expected":{"requiredDocumentIds":["..."],"requiredChunkIds":["..."],"minRecallAtK":1}}
```

答案质量 case：

```json
{"id":"case-003","caseType":"ANSWER","dataset":"frozen","input":{"message":"没有订单号可以退款吗","history":[]},"expected":{"mustContain":["订单号"],"mustNotContain":["已退款"],"groundedOnly":true}}
```

## 后台页面

新增静态页面：

- `kb-app/src/main/resources/static/traces.html`
- `kb-app/src/main/resources/static/evals.html`

页面保持最小可用：

- 左侧过滤条件，右侧表格。
- trace 详情用 tabs 展示：Summary / Prompt / Steps / Raw JSON / Replay。
- eval case 详情允许直接编辑 JSON。
- eval run 展示汇总指标和失败列表。

不做复杂标注工作流；人工补标先编辑 JSON。

## 实施阶段

| 阶段 | 内容 | 验证 | 二期优化点 |
|------|------|------|------------|
| Phase 0 | 建表迁移、model/mapper/service 骨架、配置项 | Maven 编译通过，mapper 单测覆盖插入/查询 | raw payload 迁移到对象存储，PG 仅保留 URI/hash/size |
| Phase 1 | `TraceRecorder` + Agentic RAG / 普通 RAG 接入 | 本地请求生成 trace，能看到检索/rerank/答案 | 抽象统一 tool wrapper，减少各 Agent 手工埋点重复 |
| Phase 2 | 客服助手、售后域、投诉域工具接入 | HITL 中断和投诉升级 trace 可查 | 覆盖投诉执行器、后续新增业务域和更多副作用工具 |
| Phase 3 | Trace 查询 API + replay 导出 | 按 traceId 导出 JSON，可人工复现 | 支持 replay 包版本化、差异对比和批量导出 |
| Phase 4 | Eval case 表、JSONL 导入导出、从 trace 生成草稿 | JSONL round-trip 无损 | 增加标注工作流、审核状态、样本分层采样和去重 |
| Phase 5 | Eval replay harness + 三类门禁 | smoke set 可跑，失败输出明确原因 | 引入 LLM-as-judge、趋势报表、模型/提示词版本对比 |
| Phase 6 | 最小后台页面 | 管理员可查 trace、导出 replay、生成 eval case | 升级为完整观测控制台，支持聚合指标、失败聚类和权限细分 |

## 评估门禁建议

第一版先支持阈值配置，默认：

| 指标 | 默认阈值 |
|------|----------|
| 路由/工具选择 case 通过率 | `>= 0.97` |
| 禁止副作用工具误调用数 | `0` |
| 检索 required chunk/doc 命中率 | `>= 0.90` |
| 答案断言通过率 | `>= 0.95` |
| 幻觉/无证据回答数 | `0` |

CI 可用环境变量门控，类似现有 `INTENT_EVAL=true`：

- `AGENT_TRACE_EVAL=true`
- `AGENT_TRACE_EVAL_DATASET=smoke`

## 保留与清理

虽然第一版 FULL_RAW 全量落库，仍应准备定时清理：

- raw payload 默认保留 30 天。
- trace 结构化元数据默认保留 180 天。
- eval case / eval run 长期保留。

第一版可以先提供手动清理 API 或命令；后续再加 `@Scheduled` 清理任务。

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| PostgreSQL 体积快速增长 | payload 上限、采样率、保留周期、后续对象存储迁移 |
| 敏感数据暴露 | 管理员权限、敏感 key 遮挡、导出审计 |
| 异步 step 丢失 | trace 外壳同步写；HITL 中断同步写 |
| 埋点遗漏 | 第一版覆盖核心 Agent/工具；新增工具必须通过 `TraceRecorder` helper |
| 真实重放造成副作用 | 第一版只 mock replay，不执行副作用工具 |

## 非目标

- 不做 OpenTelemetry/Jaeger 集成。
- 不做 OpenSearch 日志平台。
- 不做 provider HTTP 抓包级记录。
- 不做 token-by-token 流式记录。
- 不做复杂标注平台。
- 不做生产副作用真实重放。

## 相关页面

- [[decisions/adr-009-agent-trace-replay-eval]] — 架构决策。
- [[decisions/adr-008-intent-routing-two-tier]] — 路由评估依赖 trace 沉淀。
- [[features/intent-routing]] — 两层路由功能。
- [[features/hitl-after-sales]] — 售后 HITL。
- [[features/complaint-escalation]] — 投诉升级。
