---
created: 2026-06-04
tags: [tracing, langfuse, observability, architecture, aop, reactor, spring-ai, refactor]
---

# LangFuse Tracing 边界化重构

> 状态：**5 节方案均已落地到代码**（ingest 时 grep 核实，详见每节「代码现状」）。源文档 `docs/langfuse-tracing-architecture.md` 以「待重构实现」口吻写就，但代码已走到目标态——本页按代码实际状态记录。
> 源设计：`docs/langfuse-tracing-architecture.md`（自 `todo.md` 第 3 点整理）
> 母页：[[features/langfuse-tracing]]（总体架构 / 配置门禁 / 基础设施）
> 架构决策：[[decisions/adr-015-langfuse-tracing]]（本重构落实并修订其 C2/C3/C4）

## 这次重构在解决什么

LangFuse tracing 第一版把 tracing 生命周期代码**散落在 Service 里**：根 span 在 `MdQnAServiceImpl.ask()` / `MdAgenticQnAServiceImpl.ask()` 内用 `TracingSupport.root(...)` 自建，流式还要在 Service 内手工管 `Observation start/stop` + Reactor Context + 输出聚合；并行检索调用点手写 `ContextSnapshot.captureAll().wrapExecutor(...)`；tool span 用自定义 `gen_ai.tool.execution`；`kb.retrieval.vector` 手工包一层。

本重构的统一判断：**tracing 是横切关注点，不该长期住在业务方法体里**。一句话拆五刀：

```text
边界负责 root span 出生与死亡        （第 1 节，Controller AOP）
传播设施负责跨线程搬运 root/holder   （第 2 节，传播型 executor）
全局 filter 负责写 LLM / tool 正文   （第 3 / 4 节，ObservationFilter）
检索按所有权划埋点边界               （第 5 节，原生优先 + 业务 span 保留）
Service 只做业务：检索 / prompt / 调模型 / 存会话
```

## 1. 根 span 边界化：Service 不再自建 root

**核心判断**：「一次请求 = 一棵 trace 树」里的"一次请求"是**边界概念**，不是业务概念。Service 不知道自己是否处于请求根位置（可能被 Controller、另一个 Service、定时任务、未来 MQ 入口调用），让 Service 自建 root 等于让它猜调用栈位置——职责错位。根 span 的归属地是**请求进来的那道门**。

**边界选型**：HTTP 问答根 span 落在 **Controller 方法 AOP `@Around`**。关键理由是「决定 root span 何时关闭」需要知道本次返回的是同步结果还是一条流，只有方法级 AOP 在 `proceed()` 返回后拿得到定型后的返回值类型：

| 候选位置 | 拿得到 | 致命问题 |
|---|---|---|
| Servlet Filter | request/response | 拿不到 Controller 返回值；流式时 `doFilter()` 返回 ≠ token 产出完成，会过早关 root |
| `HandlerInterceptor` | handler/path var | `postHandle` 看不到响应体，判不出返回值是否流式 |
| **Controller 方法 AOP** | 方法参数 / SecurityContext / 定型返回值（DTO / `Flux`） | 可按返回值类型决定同步关闭还是交给流终止信号关闭 ✅ |

**同步 vs 流式生命周期**：
- 同步：`proceed()` 期间 `openScope()`，方法返回后写 output、`finally` 中 `stop()`（方法返回 = 请求完成）。
- 流式：`proceed()` 返回 `Flux` 后**关当前线程 scope 但 observation 不停**，root span 所有权转交 `Flux.doFinally(...)`——`complete` / `error` / `cancel`（客户端断开 SSE）都必须关 root，避免泄漏。必须 `doFinally` 不能只 `doOnComplete`。
- 流式还须显式 `contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, obs))`，否则 Spring AI / graph / tool 在 Reactor 内部建的 observation 找不到父节点。`Hooks.enableAutomaticContextPropagation()` 只负责线程切换时恢复，**不会**凭空把 observation 放进 Reactor Context。

**sessionId 提到边界**：`sessionId = req.sessionId != null ? req.sessionId : UUID.randomUUID()` 在 Controller 生成，非空传入 Service。理由：sessionId 是本次请求身份，root span 建立时就需要稳定的 LangFuse session 维度，不应在 Service 内临时出生再晚绑定补丁。

**代码现状（已落地）**：
- `kb-search/.../tracing/QaObserved.java`（注解）+ `QaObservedAspect.java`（`@Around` 切面）+ `AiStreamTracingSupport.java`（Reactor 流式生命周期）。
- `MdQnAController` 四端点全部挂 `@QaObserved(name = "kb.qa.ask" / "kb.qa.ask.agentic" / "kb.qa.ask.stream" / "kb.qa.ask.agentic.stream")`。
- `grep "TracingSupport.root" kb-search` **零命中** → Service 已不再自建 root span。
- 测试：`QaObservedAspectTest` / `MdQnAServiceImplStreamTracingTest` / `MdAgenticQnAServiceImplStreamTest`。

## 2. 跨线程传播设施化：调用点不再手工 wrap

**核心判断**：传播也是横切关注点。业务代码不该在每个 `CompletableFuture.supplyAsync(...)` 前手写 `ContextSnapshot.captureAll().wrapExecutor(...)`——开发者迟早漏。正确方向：**传播能力成为 executor 的构造属性**，调用方只注入「传播型 executor」，正常提交任务即可。

**关键陷阱**：不要在 Bean 初始化时执行一次 `ContextSnapshot.captureAll().wrapExecutor(delegate)`——那捕获的是**应用启动时的空上下文**。正确做法是**每次任务提交时捕获当前上下文**：

```java
class ContextPropagatingExecutor implements Executor {
    private final Executor delegate;
    public void execute(Runnable command) {
        ContextSnapshot snapshot = ContextSnapshot.captureAll();  // 提交时捕获
        delegate.execute(snapshot.wrap(command));
    }
}
```

`ContextSnapshot.captureAll()` 捕获当前线程所有已注册 `ThreadLocalAccessor`，含 Micrometer `ObservationThreadLocalAccessor` 与项目 `TracingContextHolder`。

**与 Reactor 传播的分工**（两者是补集，别混淆）：
- `Hooks.enableAutomaticContextPropagation()`：只解决 **Reactor 链内部**线程切换，前提是 observation/holder 已在 Reactor Context 里。
- 传播型 executor / `TaskDecorator`：解决**跳出 Reactor 的异步边界**（`CompletableFuture`、`@Async`、普通/虚拟线程池）。

**代码现状（已落地）**：
- `kb-common/.../tracing/ContextPropagatingExecutor`（提交时捕获）。
- `AppConfig`：`@Bean("retrievalExecutor")` = `ContextPropagatingExecutor(Executors.newVirtualThreadPerTaskExecutor())`。
- `MdHybridSearchServiceImpl` 注入 `retrievalExecutor` 跑并行检索；调用点**不再出现** `ContextSnapshot.captureAll()`（已从 `ForkJoinPool.commonPool()` + 手写 wrap 迁出）。
- `spring.ai.alibaba.tool.async.enabled=false` 作补充（减少框架不可控异步切换），非主方案。

## 3. Input/Output 与子 span 属性全局化

**核心判断**：I/O 映射、子 span 业务属性继承、脱敏截断都是 tracing 横切关注点，不该散在每次模型调用附近。收益最大的一刀是**注册全局 ObservationFilter / SpanProcessor / Interceptor**。

**规则矩阵**（谁创建谁负责写，但 LLM 正文交给全局 filter）：

```text
Controller root span 的 input/output  →  Controller AOP 统一写，Service 不写
Spring AI ChatModel generation I/O    →  ChatModelContentObservationFilter 全局写，Service 不写
自定义业务 span（retrieval/ingest/tool）→ 创建该 span 的 helper / interceptor 写
脱敏截断                              →  源头按语义上限 + SensitiveDataObservationFilter 全局兜底
```

**仍应显式写 I/O 的**：自定义业务 span 没有框架 context 推断语义，必须自己写清楚——`kb.retrieval.rerank`（query / topN 摘要）、`kb.retrieval.parent_expansion`（child hit / parent 摘要）、`kb.retrieval.vector|keyword`（query/topK/命中摘要）、`kb.ingest.*`（documentId/filename → status/chunkCount）。**embedding span 不写 I/O**（向量对人无价值、体量大）。

**代码现状（已落地）**：`ChatModelContentObservationFilter`、`SensitiveDataObservationFilter`、`LangfuseChildAttributeSpanProcessor`、`ToolCallingContentLangfuseObservationFilter` 均在 `kb-app/.../tracing`，由 `TracingConfig` 装配。详见 [[features/langfuse-tracing]] §Input/Output 映射。

## 4. Tool span：ReactAgent ToolInterceptor 复用 Spring AI 规约

**核心判断**：Alibaba `ReactAgent` 1.1.2.2 工具执行路径**不走** Spring AI `DefaultToolCallingManager`（本地 `javap` 核实：`AgentToolNode → executeToolCallWithInterceptors → ... → ToolCallback.call`，无 `ToolCallingManager.executeToolCalls`）。因此 tool span 的挂点锁定为 Alibaba `ToolInterceptor`——但 interceptor 只负责「接入位置」，**不发明新规约**，命名/字段全部复用 Spring AI 原生 `spring.ai.tool` 约定：

```text
tool span = Alibaba ToolInterceptor + Spring AI ToolCallingObservation 规约
```

| 项 | 值 | 来源 |
|---|---|---|
| observation name | `spring.ai.tool` | `DefaultToolCallingObservationConvention` |
| low-card | `spring.ai.kind` / `spring.ai.tool.definition.name` / `gen_ai.operation.name` / `gen_ai.system` | `ToolCallingObservationDocumentation` |
| high-card | `spring.ai.tool.call.arguments` / `spring.ai.tool.call.result` | 同上 |

**ToolDefinition 获取**：`ToolCallRequest` 只给 name/arguments/toolCallId，拿不到完整 `ToolDefinition`。方案是构建 ReactAgent 时把 `ToolCallback` 列表传给 interceptor，按 toolName 建索引取真实 definition/metadata；缺失才用最小 fallback。

**Langfuse 镜像**：Spring AI 原生只写 `spring.ai.tool.call.arguments/result` 高基数 key，而 LangFuse UI 要 `langfuse.observation.input/output`——故第 3 节的全局 filter 补一条 `ToolCallingObservationContext` 映射（args → input，result → output），脱敏仍在全局兜底。

**代码现状（已落地）**：
- `kb-search/.../ai/TracingToolInterceptor.java`：用 `ToolCallingObservationContext.builder()` + `ToolCallingObservationDocumentation.TOOL_CALL` + `DefaultToolCallingObservationConvention`，span 名 `spring.ai.tool`。
- `gen_ai.tool.execution` 自定义 span 名**已废弃移除**（grep 全项目零命中）。
- `kb-app/.../tracing/ToolCallingContentLangfuseObservationFilter.java`：接管 args/result 脱敏并镜像到 LangFuse input/output。

## 5. 检索链路观测边界：Vector 原生优先，业务段保留自定义 span

**核心判断**：检索链路不是「零自定义埋点」，按**所有权**划边界——Spring AI / VectorStore 原生能力优先吃原生，项目自己的 RAG 业务阶段保留少量自定义 span。

| 段 | 归属 | 处理 |
|---|---|---|
| `vector` | Spring AI VectorStore 原生 | **去掉手写** `kb.retrieval.vector`，吃 `MilvusVectorStore.similaritySearch(...)` 原生 observation（`mdVectorStore` 已注入 registry），避免与 `db.vector.*` 重叠 |
| `keyword` | 项目 SQL（TRGM/BM25） | 保留 `kb.retrieval.keyword`（原生覆盖不了），可从手写 span 迁 `@Observed` |
| `rerank` | 项目精排阶段 | 保留 `kb.retrieval.rerank`（query / candidate count / topN / 结果摘要） |
| `parent_expansion` | 项目 small-to-big 展开 | 保留 `kb.retrieval.parent_expansion`（child hit 数 / maxParents / parent 摘要） |

**`@Observed` 注意**：普通 `@Observed` 不天然知道如何从返回值生成 LangFuse output，要高质量摘要仍需自定义 `ObservationConvention` / AOP / retrieval helper——别为「声明式」牺牲 I/O 可读性。

**代码现状（已落地）**：`MdHybridSearchServiceImpl` 行内注释确认「vector 检索不再额外包 `kb.retrieval.vector`，交给 Spring AI VectorStore 原生 observation 产 span」；keyword/rerank/parent_expansion 业务 span 保留。

## 与母页的关系

[[features/langfuse-tracing]] 描述 LangFuse tracing 的**总体形态**（OTLP 管线、配置门禁、基础设施增量、span 树全景），其中关于 root span 归属、tool span 命名、并行检索传播、vector 埋点的若干具体描述写于本重构之前，已被本页修订——母页已加 `[!stale]` 指回本页。

## 关联

- 母页 / 总体架构：[[features/langfuse-tracing]]
- 架构决策（本重构落实 C2/C3/C4）：[[decisions/adr-015-langfuse-tracing]]
- 被观测的检索链路：[[features/markdown-structure-rag]] · [[features/md-keyword-bm25]]
- AI Provider 体系：[[ai-rag/providers]]
- 源设计：`docs/langfuse-tracing-architecture.md`
