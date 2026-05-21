# Progress Log: Agent Trace、离线复现包与评估回放

## Session: 2026-05-18

### Planning Setup

- **Status:** complete
- **Started:** 2026-05-18
- Actions taken:
  - Used `planning-with-files` workflow as requested.
  - Checked existing `.planning` directory and active plan.
  - Created isolated plan directory `.planning/2026-05-18-agent-trace-eval/`.
  - Generated `task_plan.md` from ADR-009 and feature design.
  - Generated `findings.md` with requirements, codebase findings, decisions, APIs and resources.
  - Generated `progress.md` with this session log.
  - Updated `.planning/.active_plan` to `2026-05-18-agent-trace-eval`.
- Files created/modified:
  - `.planning/2026-05-18-agent-trace-eval/task_plan.md`
  - `.planning/2026-05-18-agent-trace-eval/findings.md`
  - `.planning/2026-05-18-agent-trace-eval/progress.md`
  - `.planning/.active_plan`

### Implementation

- **Status:** pending
- Next phase:
  - Phase 0: 建表迁移、模型/Mapper/Service 骨架与配置项。

## Test Results

| Test | Input | Expected | Actual | Status |
|------|-------|----------|--------|--------|
| Planning file creation | `ls .planning/2026-05-18-agent-trace-eval` | Three planning files exist | `task_plan.md`, `findings.md`, `progress.md` exist | pass |
| Active plan pointer | `cat .planning/.active_plan` | `2026-05-18-agent-trace-eval` | `2026-05-18-agent-trace-eval` | pass |

## Error Log

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| — | — | — | — |

## 5-Question Reboot Check

| Question | Answer |
|----------|--------|
| Where am I? | Plan generation complete; implementation has not started |
| Where am I going? | Phase 0: database migration, model/mapper/service skeleton, trace config |
| What's the goal? | Implement unified Agent Trace, replay package and eval replay capability |
| What have I learned? | See `findings.md` |
| What have I done? | Created the isolated planning files and active plan pointer |

## Session: 2026-05-19

### Design Diagrams

- **Status:** complete
- Actions taken:
  - Added Mermaid data flow diagram to `wiki/features/agent-trace-eval.md`.
  - Added Mermaid overall architecture diagram showing runtime links, trace recorder, PostgreSQL storage, admin UI/API, replay and eval services.
  - Added Mermaid ER diagram for `agent_traces`, `agent_trace_steps`, `eval_cases`, `eval_runs`, `eval_run_results`.
  - Added Mermaid sequence diagram for trace detail, replay export, eval case generation and eval run.
- Files created/modified:
  - `wiki/features/agent-trace-eval.md`
  - `.planning/2026-05-18-agent-trace-eval/progress.md`

## Session: 2026-05-20

### Phase 0: 建表迁移、骨架与配置

- **Status:** complete
- Actions taken:
  - Added Liquibase migration `025-add-agent-traces-eval.sql` for `agent_traces`, `agent_trace_steps`, `eval_cases`, `eval_runs`, `eval_run_results`.
  - Updated `db.changelog-master.xml`.
  - Added trace/eval model classes and MyBatis mapper interfaces/XML files.
  - Added `TraceProperties` and `application.yml` defaults under `enterprise.kb.trace`.
  - Added `traceExecutor` virtual-thread executor in `AppConfig`.
  - Added `TraceRedactionService`, `TraceRecorder`, `AgentTraceService`, `EvalCaseService`, `EvalRunService` skeleton implementations.
  - Added unit tests for trace redaction and trace property defaults.
- Files created/modified:
  - `kb-app/src/main/resources/db/changelog/025-add-agent-traces-eval.sql`
  - `kb-app/src/main/resources/db/changelog/db.changelog-master.xml`
  - `kb-app/src/main/java/com/enterprise/kb/AppConfig.java`
  - `kb-app/src/main/resources/application.yml`
  - `kb-search/src/main/java/com/enterprise/kb/search/config/TraceProperties.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/model/AgentTrace.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/model/AgentTraceStep.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/model/EvalCase.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/model/EvalRun.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/model/EvalRunResult.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/mapper/AgentTraceMapper.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/mapper/AgentTraceStepMapper.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/mapper/EvalCaseMapper.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/mapper/EvalRunMapper.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/mapper/EvalRunResultMapper.java`
  - `kb-search/src/main/resources/mapper/AgentTraceMapper.xml`
  - `kb-search/src/main/resources/mapper/AgentTraceStepMapper.xml`
  - `kb-search/src/main/resources/mapper/EvalCaseMapper.xml`
  - `kb-search/src/main/resources/mapper/EvalRunMapper.xml`
  - `kb-search/src/main/resources/mapper/EvalRunResultMapper.xml`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/TraceRecorder.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/TraceRedactionService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/AgentTraceService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/EvalCaseService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/EvalRunService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRecorderImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/TraceRedactionServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgentTraceServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/EvalCaseServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/EvalRunServiceImpl.java`
  - `kb-search/src/test/java/com/enterprise/kb/search/config/TracePropertiesTest.java`
  - `kb-search/src/test/java/com/enterprise/kb/search/service/impl/TraceRedactionServiceImplTest.java`

### Verification

- **Status:** complete
- Actions taken:
  - First Maven run failed because the active Java was not JDK 21.
  - Re-ran with `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`.
  - `mvn test -pl kb-search -am` passed.
- Test result:
  - 105 tests run, 0 failures, 0 errors, 1 skipped.

### Error Log Update

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-05-20 | `invalid target release: 21` | 1 | Re-ran Maven with project JDK 21 path in `JAVA_HOME` and `PATH` |

### 5-Question Reboot Check

| Question | Answer |
|----------|--------|
| Where am I? | Phase 0 complete |
| Where am I going? | Phase 1: connect `TraceRecorder` into standard RAG and Agentic RAG |
| What's the goal? | Implement unified Agent Trace, replay package and eval replay capability |
| What have I learned? | See `findings.md` |
| What have I done? | Created trace/eval schema, model/mapper/service skeleton, config, redaction tests, and verified kb-search tests pass |

### Acceptance Report

- **Status:** complete
- Actions taken:
  - Created Phase 0 acceptance report summarizing scope, deliverables, verification command, test result, risks and next steps.
- Files created/modified:
  - `.planning/2026-05-18-agent-trace-eval/acceptance_report.md`

### Table UML

- **Status:** complete
- Actions taken:
  - Created a PlantUML table relationship diagram for this feature's trace/eval tables.
  - Included the five new tables and weak logical links to existing `users` and `spaces`.
- Files created/modified:
  - `.planning/2026-05-18-agent-trace-eval/agent-trace-eval-tables.puml`

### Acceptance Report Code Pointers

- **Status:** complete
- Actions taken:
  - Added "核心代码位置" to every acceptance scenario in the functional acceptance report.
  - Existing Phase 0 scenarios point to implemented migration/config/service/mapper/test files.
  - Future Phase 1+ scenarios point to planned integration files and API/page locations.
- Files created/modified:
  - `.planning/2026-05-18-agent-trace-eval/acceptance_report.md`

### Phase 1: RAG Trace 接入

- **Status:** complete for code wiring; pending database runtime acceptance
- Trigger:
  - User found `TraceRecorderImpl#startTrace` had no production callers.
- Actions taken:
  - Connected `QnAServiceImpl.ask(...)` to `TraceRecorder.startTrace(...)` with trace type `STANDARD_QA`.
  - Added standard RAG steps for `QUERY_REWRITE`、`HYDE`、`RETRIEVAL`、`RERANK`、`MODEL_CALL` and final `completeTrace(...)`.
  - Connected `AgenticQnAServiceImpl.ask(...)` to `TraceRecorder.startTrace(...)` with trace type `AGENTIC_QA`.
  - Added Agentic `searchKnowledgeBase` tool step capture for success, skipped and failed tool calls.
  - Added Agentic final `MODEL_CALL` step and `completeTrace(...)`.
  - Added fail trace handling around standard and Agentic QA runtime failures.
  - Replaced trace payload maps in `QnAServiceImpl` with null-safe `traceMap(...)` to avoid `Map.of(...)` null failures.
- Files created/modified:
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java`
- Verification:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH mvn test -pl kb-search -am`
  - Result: 105 tests run, 0 failures, 0 errors, 1 skipped.

### Phase 1 Error Log Update

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-05-20 | `QnAServiceImpl` lambda 捕获非 effectively final 的 `tokensUsed` 导致编译失败 | 1 | 使用已固定的 `finalTokensUsed` 写入 `completeTrace(...)` |

### 5-Question Reboot Check Update

| Question | Answer |
|----------|--------|
| Where am I? | Phase 1 RAG trace code wiring complete |
| Where am I going? | Run database/API acceptance for RAG traces, then enter Phase 2 customer/after-sales/complaint trace wiring |
| What's the goal? | Ensure trace data is actually produced from production QA paths, not only available as an unused recorder |
| What have I learned? | The first implementation missed production callers for `TraceRecorder.startTrace(...)`; both RAG entry points now call it |
| What have I done? | Wired standard RAG and Agentic RAG to start/step/complete/fail trace calls and verified `kb-search` tests pass |

## Session: 2026-05-20 Continued

### Phase 2-6: 最小闭环实现

- **Status:** complete for minimal code; pending runtime DB/API/browser acceptance
- Actions taken:
  - Added customer assistant trace envelope for routed and legacy paths.
  - Added routed guard/router/final-answer trace steps.
  - Passed trace context into domain handlers.
  - Added after-sales tool/HITL trace recording.
  - Added complaint escalation tool and complaint business-ref trace recording.
  - Added admin trace APIs: list, detail, replay export, create eval case draft.
  - Added eval case APIs: list, detail, create, update, JSONL import/export.
  - Added minimal eval replay service and eval run APIs.
  - Added static admin pages `traces.html` and `evals.html`.
  - Added `NoopTraceRecorder` for direct-constructor unit test compatibility.
- Files created/modified:
  - `kb-search/src/main/java/com/enterprise/kb/search/dto/DomainContext.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/AfterSalesDomainHandler.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/ComplaintDomainHandler.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/NoopTraceRecorder.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/controller/AgentTraceController.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/controller/EvalCaseController.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/controller/EvalRunController.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/EvalReplayService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/EvalReplayServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/EvalCaseService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/EvalCaseServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/EvalRunService.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/service/impl/EvalRunServiceImpl.java`
  - `kb-search/src/main/java/com/enterprise/kb/search/mapper/EvalCaseMapper.java`
  - `kb-search/src/main/resources/mapper/EvalCaseMapper.xml`
  - `kb-app/src/main/resources/static/traces.html`
  - `kb-app/src/main/resources/static/evals.html`
  - `.planning/2026-05-18-agent-trace-eval/task_plan.md`
  - `.planning/2026-05-18-agent-trace-eval/acceptance_report.md`
- Verification:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH mvn test -pl kb-search -am`
  - Result: 105 tests run, 0 failures, 0 errors, 1 skipped.
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH mvn test -pl kb-app -am`
  - Result: reactor build success.

### Phase 2-6 Error Log Update

| Timestamp | Error | Attempt | Resolution |
|-----------|-------|---------|------------|
| 2026-05-20 | 新增 `TraceRecorder` 构造参数导致旧单测直接构造服务失败 | 1 | 新增 `NoopTraceRecorder` 和兼容构造器 |
| 2026-05-20 | `AfterSalesDomainHandlerTest` 覆写 `buildAgent(String)` 的测试 seam 被双参数方法绕过 | 1 | 恢复单参数 `buildAgent(String)` 调用，并用 `ThreadLocal` 传递生产 traceId |
