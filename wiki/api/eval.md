# 离线评估 API（admin）

控制器：`EvalRunController` + `EvalCaseController`（kb-search 模块），管理员视角。

支撑 [[features/ragas-evaluation]]：用例库（`eval_cases`）+ 评估运行（`eval_runs`，含 Ragas LLM-as-judge 打分）。详见 [[decisions/adr-014-ragas-integration]]。

## Eval Cases 用例库 — `/api/v1/admin/eval-cases`

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/` | 用例列表（支持过滤 `tag` / `domain` 等） |
| `GET` | `/{id}` | 用例详情 |
| `POST` | `/` | 新增用例 |
| `PUT` | `/{id}` | 修改用例 |
| `POST` | `/import-jsonl` | 批量导入（`Content-Type: text/plain`，按行 jsonl） |
| `GET` | `/export-jsonl` | 批量导出 jsonl |

### 用例模型

```jsonl
{"question": "...", "expectedAnswer": "...", "tag": "...", "domain": "...", "context": [...]}
```

## Eval Runs 评估运行 — `/api/v1/admin/eval-runs`

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/` | 启动一次评估运行（指定用例集 + 被评估 service：`MdQnA` / `MdAgenticQnA` / `CustomerAssistant` 等） |
| `GET` | `/{id}` | 评估运行详情（含每例 Ragas 打分聚合：faithfulness / answer_relevancy / context_precision 等） |

### 启动评估

```json
POST /api/v1/admin/eval-runs
{
  "caseTag": "after-sales",
  "service": "MdAgenticQnAService",
  "modelProvider": "LLAMA_CPP",
  "ragasMetrics": ["faithfulness", "answer_relevancy", "context_precision"]
}
```

## 相关页面

- [[features/ragas-evaluation]] — 评估方法学与 Ragas 集成
- [[decisions/adr-014-ragas-integration]] — 架构决策
