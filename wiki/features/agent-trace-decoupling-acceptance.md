# Agent Trace 接入解耦验收报告

## 范围

本文对应 ADR-010 的 Trace 解耦实现：建立 Trace 解耦基础层，并完成普通 RAG、Agentic RAG、客服、售后、投诉域的业务代码解耦。

已覆盖能力：

- Trace Facade 与 Trace Scope。
- TraceContext 与线程绑定恢复。
- Spring AI Alibaba `ToolInterceptor` 采集工具调用。
- Spring AI Alibaba `ModelInterceptor` 采集 Agent 模型调用。
- Spring AI `CallAdvisor` 采集普通 ChatClient 模型调用。
- ReactAgent Builder 工厂统一注入 Trace 拦截器。
- `TraceTurnAspect` 统一管理外层 turn 生命周期：start、bind、complete、fail。
- `QnAServiceImpl` 已移除 `TraceRecorder`、Trace DTO、`jsonOf(...)`、`traceMap(...)`、`traceFacade.start(...)`、`trace.complete(...)`、`trace.fail(...)`。
- `AgenticQnAServiceImpl` 已移除 `TraceRecorder`、Trace DTO、`jsonOf(...)`、`traceMap(...)`，并通过 `TraceReactAgentFactory` 构建 ReactAgent。
- `CustomerAssistantServiceImpl` 已移除 `TraceRecorder`、Trace DTO、`jsonOf(...)`、`traceMap(...)`，turn 生命周期由 `TraceTurnAspect` 管理。
- `AfterSalesDomainHandler` 已移除裸 `ThreadLocal<UUID>` traceId，HITL 中断通过 `TraceEvent` 补充业务语义。
- `ComplaintDomainHandler` 已移除手写工具调用 trace，投诉业务引用通过 `TraceEvent` 补充。
- 架构守护测试已覆盖普通 RAG、Agentic RAG、客服、售后、投诉域的禁用依赖。

## 场景 1：Trace Facade 可创建 turn 级 Trace Scope

**前置条件**

- `TraceRecorder.startTrace(...)` 返回一个 traceId。

**操作**

1. 调用 `TraceFacade.start(...)`。
2. 传入 traceType、agentName、sessionId、spaceId、userId、modelProvider、rawInput。

**期望结果**

- 返回 `enabled=true` 的 `TraceScope`。
- `TraceScope.context()` 包含 traceId、traceType、agentName、sessionId、spaceId、userId。
- rawInput 被序列化后交给 `TraceRecorder.startTrace(...)`。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/TraceFacade.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/impl/TraceFacadeImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/impl/TraceFacadeImplTest.java`

## 场景 2：Trace 未创建时自动退化为 noop

**前置条件**

- trace 配置关闭、未命中采样，或 `TraceRecorder.startTrace(...)` 写入失败，返回 `Optional.empty()`。

**操作**

1. 调用 `TraceFacade.start(...)`。
2. 对返回 scope 调用 `event(...)`、`complete(...)`、`fail(...)`。

**期望结果**

- 返回 `NoopTraceScope`。
- `enabled=false`。
- 后续 event/complete/fail 不写 step，不影响业务流程。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/NoopTraceScope.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/impl/TraceFacadeImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/impl/TraceFacadeImplTest.java`

## 场景 3：Trace event 自动生成 stepIndex 并序列化 payload

**前置条件**

- 已创建有效 `TraceScope`。

**操作**

1. 连续调用两次 `scope.event(...)`。
2. 第一次传 `RETRIEVAL` 事件。
3. 第二次传 `TOOL_CALL` 事件。

**期望结果**

- 第一个 step 的 `stepIndex=1`。
- 第二个 step 的 `stepIndex=2`。
- input/output 对象由 facade 统一 JSON 序列化。
- TOOL_CALL 事件自动写入 `toolName` 和 `toolCallId`。
- 业务代码无需手写 `TraceStepRequest`、`jsonOf(...)`、`traceMap(...)`。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/TraceEvent.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/impl/TraceFacadeImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/impl/TraceFacadeImplTest.java`

## 场景 4：Trace complete 幂等

**前置条件**

- 已创建有效 `TraceScope`。

**操作**

1. 调用 `scope.complete(output, tokensUsed)`。
2. 再调用 `scope.close()`。

**期望结果**

- `TraceRecorder.completeTrace(...)` 只被调用一次。
- rawOutput 被统一 JSON 序列化。
- tokensUsed 被传递。
- 防止业务 finally/try-with-resources 路径重复完成 trace。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/impl/DefaultTraceScope.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/impl/TraceFacadeImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/impl/TraceFacadeImplTest.java`

## 场景 5：Spring AI Alibaba ToolInterceptor 记录成功工具调用

**前置条件**

- 当前线程已通过 `TraceContextHolder.bind(scope)` 绑定有效 Trace Scope。
- ReactAgent 内部触发工具调用。

**操作**

1. `TraceToolInterceptor.interceptToolCall(...)` 收到 `ToolCallRequest`。
2. 下游 `ToolCallHandler` 正常返回 `ToolCallResponse`。

**期望结果**

- 写入一条 `TOOL_CALL/SUCCEEDED` 事件。
- 事件包含 toolName、toolCallId、arguments、result、status、durationMs。
- 拦截器返回原始 `ToolCallResponse`，不改变工具调用行为。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceToolInterceptor.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/TraceContextHolder.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/agent/TraceToolInterceptorTest.java`

## 场景 6：Spring AI Alibaba ToolInterceptor 记录失败工具调用并继续抛异常

**前置条件**

- 当前线程已绑定有效 Trace Scope。
- 工具执行时抛出 `RuntimeException`。

**操作**

1. `TraceToolInterceptor.interceptToolCall(...)` 调用下游 handler。
2. handler 抛出异常。

**期望结果**

- 写入一条 `TOOL_CALL/FAILED` 事件。
- 事件包含 toolName、toolCallId、arguments、errorType、errorMessage、durationMs。
- 原异常继续向上抛出，业务错误语义不被吞掉。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceToolInterceptor.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/agent/TraceToolInterceptorTest.java`

## 场景 7：AOP 统一管理 turn 生命周期

**前置条件**

- 入口方法标记 `@TraceTurn`。
- 请求未传 sessionId 或已传 sessionId。

**操作**

1. 调用普通 RAG `QnAServiceImpl.ask(...)`、Agentic RAG `AgenticQnAServiceImpl.ask(...)` 或客服 `CustomerAssistantServiceImpl.chat(...)`。
2. `TraceTurnAspect` 在进入方法前补齐新会话 ID，并创建 `TraceScope`。
3. `TraceTurnAspect` 通过 `TraceContextHolder.bind(...)` 绑定当前 scope。
4. 原方法正常返回或抛出异常。

**期望结果**

- 正常返回时由 AOP 调用 `trace.complete(...)`。
- 异常时由 AOP 调用 `trace.fail(...)` 并继续抛出原异常。
- 业务入口不再手写 `traceFacade.start(...)`、`trace.complete(...)`、`trace.fail(...)`。
- 新会话场景下，AOP 生成的 sessionId 会传入业务方法，trace 与响应中的 sessionId 一致。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/aop/TraceTurn.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/aop/TraceTurnAspect.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/TraceArchitectureTest.java`

## 场景 8：ReactAgent Builder 统一注入 Trace 拦截器

**前置条件**

- 业务代码需要创建 ReactAgent。

**操作**

1. 调用 `TraceReactAgentFactory.builder(agentName, traceContext)`。
2. 在返回 builder 上继续设置 chatClient、systemPrompt、tools、hooks、compileConfig。

**期望结果**

- Builder 已统一注入 `TraceModelInterceptor` 和 `TraceToolInterceptor`。
- 业务类不需要各自手写 `.interceptors(...)`。
- `AgenticQnAServiceImpl`、客服、售后、投诉域复用同一入口。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceReactAgentFactory.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceReactAgentFactoryImpl.java`

## 场景 9：普通 ChatClient 模型调用可通过 Advisor 采集

**前置条件**

- 普通 RAG 使用 Spring AI `ChatClient`。
- 当前线程已绑定有效 Trace Scope。

**操作**

1. 在 ChatClient 调用链中加入 `TraceChatClientAdvisor`。
2. 模型调用正常返回或抛出异常。

**期望结果**

- 正常返回时写入 `MODEL_CALL/SUCCEEDED` 事件。
- 异常时写入 `MODEL_CALL/FAILED` 事件并继续抛异常。
- Advisor 顺序在 chat memory 之后，便于记录最终进入模型的 messages。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/advisor/TraceChatClientAdvisor.java`

## 场景 10：普通 RAG 业务代码不再直接依赖 Trace DTO

**前置条件**

- 开发者查看或修改普通 RAG 主流程。

**操作**

1. 检查 `QnAServiceImpl`。
2. 搜索 `TraceRecorder`、`TraceStartRequest`、`TraceStepRequest`、`TraceCompleteRequest`、`jsonOf(`、`traceMap(`。

**期望结果**

- 上述关键字均不存在。
- turn 生命周期由 `@TraceTurn` / `TraceTurnAspect` 表达。
- query rewrite、HyDE、retrieval、rerank 通过 `TraceEvent` 记录业务语义。
- 模型调用通过 `TraceChatClientAdvisor` 记录，不再手写 `MODEL_CALL` step。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/QnAServiceImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/TraceArchitectureTest.java`

## 场景 11：Agentic RAG 业务代码不再直接依赖 Trace DTO

**前置条件**

- 开发者查看或修改 Agentic RAG 主流程。

**操作**

1. 检查 `AgenticQnAServiceImpl`。
2. 搜索 `TraceRecorder`、`TraceStartRequest`、`TraceStepRequest`、`TraceCompleteRequest`、`jsonOf(`、`traceMap(`、`recordSearchStep(`。

**期望结果**

- 上述关键字均不存在。
- ReactAgent 通过 `TraceReactAgentFactory` 构建，统一注入 `TraceToolInterceptor` / `TraceModelInterceptor`。
- `searchKnowledgeBase` 的工具调用 input/output 由 Spring AI Alibaba `ToolInterceptor` 采集。
- 检索候选、rerank 结果、withinBudget 结果由 `TraceEvent(RETRIEVAL)` 补充业务语义。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/AgenticQnAServiceImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceReactAgentFactoryImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/TraceArchitectureTest.java`

## 场景 12：客服助手 turn 生命周期与路由事件通过 AOP 和 Trace Event 记录

**前置条件**

- 调用客服助手 `chat(...)`。
- 路由架构开启或关闭均可。

**操作**

1. `TraceTurnAspect` 为 `CustomerAssistantServiceImpl.chat(...)` 创建并绑定 `TraceScope`。
2. 路由路径记录攻击守卫、域路由、最终回答事件。
3. 旧单体路径通过 `TraceReactAgentFactory` 构建 ReactAgent。
4. 正常返回时由 AOP complete，异常时由 AOP fail。

**期望结果**

- 业务代码不再出现 `TraceRecorder`、Trace DTO、`jsonOf(...)`、`traceMap(...)`。
- ReactAgent 模型/工具调用由 `TraceModelInterceptor` / `TraceToolInterceptor` 自动采集。
- HITL 中断以 `TraceEvent("HITL_INTERRUPT", ...)` 补充审核单语义。
- Trace 写入失败不影响客服对话响应。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/aop/TraceTurnAspect.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/TraceScope.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceReactAgentFactory.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/TraceArchitectureTest.java`

## 场景 13：售后域移除 ThreadLocal traceId 并复用 Agent 拦截器

**前置条件**

- 客服路由命中 `AFTER_SALES` 域。
- 外层已通过 `TraceContextHolder.bind(trace)` 绑定当前 Trace。

**操作**

1. `AfterSalesDomainHandler.handle(...)` 执行售后域逻辑。
2. `buildAgent(...)` 使用 `TraceReactAgentFactory` 构建售后 ReactAgent。
3. 订单售后资格查询工具被模型调用。
4. 需要人工审核时触发 HITL 中断。

**期望结果**

- 售后域不再维护 `ThreadLocal<UUID>` 或 `activeTraceId`。
- 工具调用由 `TraceToolInterceptor` 记录 input/output、toolName、toolCallId、durationMs。
- HITL 中断由领域 `TraceEvent` 补充 `REVIEW_REQUEST` 业务引用。
- 售后业务代码不再直接依赖 Trace DTO。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/AfterSalesDomainHandler.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/TraceContextHolder.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceToolInterceptor.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/TraceArchitectureTest.java`

## 场景 14：投诉域工具调用和业务引用解耦

**前置条件**

- 客服路由命中 `COMPLAINT` 域。
- 外层已绑定当前 Trace。

**操作**

1. `ComplaintDomainHandler.handle(...)` 构建投诉域 ReactAgent。
2. 模型调用 `escalateComplaint` 工具。
3. 投诉单创建成功后启动处理计划。

**期望结果**

- `escalateComplaint` 工具调用由拦截器自动采集。
- 投诉单 ID 通过 `TraceEvent("BUSINESS_REF", ...)` 记录 `businessRefType=COMPLAINT` 和 `businessRefId`。
- 投诉域不再出现 `TraceRecorder`、Trace DTO、`jsonOf(...)`、`traceMap(...)`、`recordToolStep(...)`。
- 对话、投诉升级、处理计划启动行为保持原语义。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/ComplaintDomainHandler.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceModelInterceptor.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/agent/TraceToolInterceptor.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/test/java/com/enterprise/kb/search/trace/TraceArchitectureTest.java`

## 场景 15：无 Trace 依赖路径使用 NoopTraceFacade

**前置条件**

- 单测或兼容构造器未注入真实 `TraceFacade`。

**操作**

1. 通过兼容构造器创建客服、售后或投诉相关服务。
2. 执行 chat/handle 业务流程。

**期望结果**

- 服务可以正常构造。
- `NoopTraceFacade` 返回空 scope，事件记录为空操作。
- 业务类不需要直接构造 `NoopTraceRecorder` 或 `TraceFacadeImpl`。

**核心代码位置**

- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/NoopTraceFacade.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/trace/NoopTraceScope.java`
- `/Users/ganendong/Documents/workspace/claude2/enterprise-kb/kb-search/src/main/java/com/enterprise/kb/search/service/impl/CustomerAssistantServiceImpl.java`

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH mvn test -pl kb-search -am
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin:$PATH mvn test -pl kb-app -am
```

## 验证结果

- `kb-search` 测试通过。
- 结果：113 tests，0 failures，0 errors，1 skipped。
- `kb-app -am` 测试通过。
- 结果：Reactor 全部 SUCCESS。
- 旧耦合符号扫描通过：普通 RAG、Agentic RAG、客服、售后、投诉域均未命中 `TraceRecorder`、Trace DTO、`TraceStartCommand`、`jsonOf(...)`、`traceMap(...)`、`ThreadLocal<UUID>`、`activeTraceId`、`traceFacade.start(...)`、`trace.complete(...)`、`trace.fail(...)`。
