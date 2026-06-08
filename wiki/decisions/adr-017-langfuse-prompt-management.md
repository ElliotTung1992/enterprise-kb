---
id: adr-017
title: LangFuse Prompt 管理
status: Proposed
date: 2026-06-08
tags: [decision, prompt-management, langfuse, hot-reload, tracing]
---

# ADR-017：LangFuse Prompt 管理

## 状态

Accepted / Implemented（2026-06-08）。P0 基础设施、P1 客服/投诉、P2 画像/图片理解范围内 prompt 均已接入 `PromptProvider`；真实 LangFuse 起栈、prompt 创建/promote、UI trace 关联仍需运行态验证。设计稿见 `docs/design-langfuse-prompt-management.md`。

## 背景

当前所有 LLM prompt 都是**硬编码 Java 文本块**（`"""…"""` + `.formatted()` 的 `%s` 占位），散落在约 15 个 `ServiceImpl`（MD 问答、Agentic、HyDE/改写、域路由、投诉/客服、画像推断、图片理解）。改一句话术 = 改代码 + 走构建 + 重新发版，运营/产品无法参与，也无版本/回滚/对比。

目标是引入 prompt 管理，**四个动机全要**：① 热更新（不改代码、不重部署即可改）② 版本化 + 回滚 + 审计 ③ prompt↔trace 关联（复用 ADR-015 已自部署的 LangFuse tracing）④ A/B / 多版本灰度。优先复用已有的 LangFuse。

两条硬约束定了整个形态：

- **Java SDK 只作底层 typed API client**。LangFuse Java SDK 已可用于 prompt API 访问，但项目不能把主链路可用性语义交给 SDK：客户端缓存、后台刷新、last-known-good、classpath fallback、`{{}}` 渲染、prompt version trace 写入仍由项目自有组件实现。
- **prompt 在问答主链路上**，可用性敏感——与 ADR-015 那种"可降级旁路"的 tracing 风险等级完全不同。LangFuse 宕机/抖动/应用启动预热失败/业务首次访问 cold miss，绝不能打挂问答。

## 决策

1. **取数机制 = Java SDK adapter + 自有缓存降级层**。`LangfusePromptClient` 薄封装 `com.langfuse:langfuse-java`，只用 `client.prompts().get(name, GetPromptRequest.label(label), RequestOptions.timeout(...))` 做 typed API 访问；Caffeine、last-known-good、本地 fallback、`{{}}` 渲染、metrics、trace 写入仍在 `LangfusePromptProvider`。不引 Py/JS sidecar、不退回配置中心。理由：SDK 能减少手写 URL/path 编码与响应类型解析错误，尤其 `kb/agentic/system` 这类带 `/` 的 name 应作为单个 path segment 编码；但主链路可用性语义必须由项目自己掌控。

2. **SoT = LangFuse 权威 + 代码降级**。三级取值链：**Caffeine 缓存(last-known-good) → LangFuse 同步拉 → 代码内置 fallback**。接受"LangFuse 不可用时回退到可能较旧的代码基线文案"这个降级语义。

3. **fallback 形态 = classpath 资源文件**（`resources/prompts/…`），不再塞 Java 文本块常量——多行可读、可 diff、可脚本对账。

4. **占位符统一 = `{{mustache}}` + 自建极简渲染器**，绕开 Spring AI `PromptTemplate`。LangFuse 与 fallback 文件同构用 `{{var}}`，运营 UI 所见即所得，两源渲染逻辑唯一；自渲染器只做变量替换（不做 mustache 的 section/loop）。**避开 Spring AI ST 引擎对 `{` 的敏感**（运营若在 prompt 放 JSON few-shot 示例会被当变量解析炸）。**缺变量 fail-fast 抛异常**（防带着 `{{context}}` 字面量发给模型）；语义为"模板有 `{{x}}` 但 vars 无该 key"才报错，提供空串合法（vars 给 `x=""` 视为合法替换，用于模板内条件留白；画像块不走模板变量、由调用方代码拼接，见设计 §6）。

5. **prompt 类型 = 全 text 类型，chat 暂不做**。text 完美吻合"system 一段 + 代码拼 user/检索/历史"的现状（动态部分不可能进 LangFuse）；text 默认作 `SystemMessage`，个别单轮指令型调用方可覆盖 role。

6. **DomainRouter 特例**：域路由的**指令段进 LangFuse text，few-shot 仍留 `jsonl`**。理由：few-shot 的 `frozen-testset.jsonl` / `fewshot-pool.jsonl` 硬切分防污染是其评估准确率命根，不塞进 LangFuse chat 破坏评估隔离（见 [[adr-008-intent-routing-two-tier]]）。

7. **命名 = `kb/<域>/<功能>`**（小写连字符，用 `/` 分层，`kb/` 占命名空间）。**三位一体：name = LangFuse prompt key = fallback 文件路径**（`kb/qa/system` ↔ prompt `kb/qa/system` ↔ `resources/prompts/kb/qa/system.txt`），同一 key 贯穿三处，无第二套映射表。

8. **取值按 label 不按 version**；环境隔离用 **label**（`KB_PROMPT_LABEL` 默认 `production`），不开多 project。运营 promote 新版本到 `production` → 生产自动热切，代码永不引用 version 号；回滚 = 旧 version 重 promote。

9. **总开关 = `PromptProvider` 接口 + `@ConditionalOnProperty` 二选一装配**（`LangfusePromptProvider` 三级链 / `LocalPromptProvider` 只读 fallback），放 `kb-common`，业务代码只注入接口、无感知。`enterprise.kb.prompt.enabled`（短 env：`KB_PROMPT_ENABLED`）**默认 OFF**——OFF = 读 fallback = 现状行为，上线零风险；与 ADR-015 tracing、intent-routing kill-switch 默认 false 心智一致。短 env var 必须在 `application.yml` 显式映射到 Spring 属性，并在 `.env.example` / `docker-compose.yml` 应用容器环境变量中声明；否则 `KB_PROMPT_ENABLED=true` 不一定触发 `@ConditionalOnProperty`。

10. **prompt↔trace 关联 = 后置增量、尽力而为、仅 Agentic**。顺序型 QA 将下线，主场景是 Agentic；Agentic 全程单一 system prompt（`composeSystemPrompt` = 基础 `kb/agentic/system` + 运行时画像块），内部多轮 reasoning 共用它 → **直接复用 ADR-015 既有的 `LangfuseChildAttributeSpanProcessor` 复制模型**（根 span 设一次 → 复制到所有子 span；LangFuse 只在 `type=generation` 的 span 认 prompt 属性，其余子 span 多余属性被忽略，无害），**砍掉**原拟的 ObservationFilter 精确关联（那是顺序型多-prompt 串味场景才需要）。attribute = `langfuse.observation.prompt.name` + `langfuse.observation.prompt.version`（**必须整数**），依赖 generation span 带 `observation.type=generation`。**必须起栈实测**，OTLP 路径不认时降级为普通 metadata（`langfuse.observation.metadata.prompt_*`，看得见能搜、无原生版本聚合面板）。

11. **缓存 = Caffeine `refreshAfterWrite(60s)` + `expireAfterWrite(24h)` + `maximumSize(100)`**。`refresh` 做热更新（生效延迟≈60s）+ 抗抖动（**失败保留旧值 = 三级链中间级兜底**，stale-while-revalidate，热路径不阻塞刷新 / 解耦问答 QPS 与 LangFuse 负载）；`expire(24h, 长)` 仅清"被删/改名的僵尸 key"、防内存泄漏，实战几乎不触发。失败**永不抛业务**，WARN + micrometer counter `kb.prompt.fetch.failure`，MVP 不接告警。**启动预热** P0+P1 prompt、失败不阻塞应用启动（早暴露配置错误）。预热成功后真实业务通常走热路径；预热失败或某 name 首次访问时仍是 cache cold miss，会同步等待一次 SDK 拉取直到 timeout，失败后 fallback。SDK prompt fetch 必须显式设置短 timeout（默认 3s）与低重试（默认 0），禁止使用 SDK 默认 60s timeout / 2 retries。

12. **Agentic token budget 必须与 prompt 同源**。`kb/agentic/system` 迁移后，`AgenticTokenBudgetServiceImpl` 不能继续用独立的 `AgenticTokenBudgetService.AGENT_SYSTEM_PROMPT` 常量估算 system prompt token；必须读取同一个 prompt 来源（至少同一个 fallback 文件，ON 态可走 `PromptProvider` 当前基础 prompt），避免 LangFuse 热更新后实际 system prompt 与预算估算漂移。运行时画像块若拼入实际 system prompt，也要按当次请求计入预算，或明确由安全 buffer 覆盖。

13. **治理 = 留痕 + 收口 + 强刹车，不上事前审批**（匹配内部工具、团队小、未上线的现实）。① 留痕靠 LangFuse 原生 version + author，**强制约定改 production 必填 commit message**；② `production` promote 权限**只给核心人**，运营在 `staging` label 编辑验证；③ 不自建 PR-style 审批，用轻流程（群里知会 / 重要话术结对）；④ 三道刹车：秒级回滚、`KB_PROMPT_ENABLED=false` 全线熔断、Agentic prompt↔trace 关联当事后雷达；⑤ 预发用 `staging` label + `KB_PROMPT_LABEL=staging` 验证后再 promote。

14. **范围分层**：**P0 `kb/agentic/system`（pilot）** → **P1 客服/投诉竖井（~6 段：域路由指令、CustomerAssistant、Complaint 责任推断/Handler/Executor、AfterSales）** → **P2 画像推断 / 图片理解**。上述范围已完成代码接入；**HyDE / 查询改写随顺序型 QA 下线，本次不纳入**（代码保留）。

15. **落地 = tracer-bullet 三阶段**：基础设施（零业务接入）→ **P1-pilot `kb/agentic/system` 端到端联调**（成败手：验证取数/渲染/fallback/开关/Agentic token budget 同源/复制模型关联全链路）→ 批量铺 P1（模式已验、复制式迁移）。每阶段先 `KB_PROMPT_ENABLED=false` 部署（行为=现状）再开 true。

## 后果

- **正**：prompt 与代码解耦、运营可热更新话术（P1 客服/投诉是价值兑现区）；版本/回滚/审计由 LangFuse 原生提供；默认 OFF + 三级 fallback，可用性不被 LangFuse 绑架、上线零风险；SDK 减少手写 API/path 编码/类型解析错误；Agentic 通过显式 `renderForTrace` 与 ADR-015 复用同一套 span 属性下传机制。
- **负**：新增 `langfuse-java` 依赖（OkHttp/Jackson 等传递依赖需锁版本观察）；自有缓存/渲染/fallback 对账仍需维护；prompt↔trace 关联走 OTLP 是灰色地带、可能落到 metadata 降级；fallback 文件与 LangFuse 双源存在"代码基线漂移"风险（缓解：脚本对账 + 仅降级时生效）；治理靠流程而非系统强制（缓解：人少 + 强回滚/熔断）。

## 备选与放弃

- **手写 `RestClient` 直连 prompt API**：放弃作为 P0 首选。API 很薄，手写可行，但 Java SDK 已覆盖 prompt get/list/create，且能处理 path segment 编码与 typed response；保留为 SDK 兼容性不达标时的替代实现。
- **Py-JS sidecar**：放弃。多部署件 + 网络跳，与单体 Spring 不匹配。
- **退回配置中心（Nacos/Apollo/DB）做 prompt**：放弃。放弃 prompt↔trace 原生关联，且再立一套基础设施。
- **Spring AI `PromptTemplate` 渲染（`{var}`）**：放弃。ST 引擎对 `{` 敏感，JSON few-shot 必炸。
- **chat 类型 prompt**：暂不做。动态部分（user/检索/历史）无法进 LangFuse，chat 反成半结构化徒增复杂度。
- **ObservationFilter + thread-local 精确关联**：本次砍掉。仅 Agentic 单 prompt 场景下复制模型已够，精确关联是顺序型多-prompt（已下线）才需要。
- **短 `expireAfterWrite`（硬新鲜度上界）**：放弃。与"可用性优先 / last-known-good 兜底"决策冲突，LangFuse 长挂时反而更早跌到代码 fallback + 周期同步尖峰。

## 待验证（运行态）

**起栈联调（与 ADR-015 共栈）**：
- **Java SDK 兼容性**：Maven 依赖可解析；自部署 `LANGFUSE_BASE_URL` 可用；Basic Auth 可用；带 `/` 的 `kb/agentic/system` name 被作为单个 path segment 编码并可正确拉取；label/version 查询可用；TextPrompt 的 `name/version/prompt` 字段完整；timeout/maxRetries 可控；异常类型能被统一转换为 fetch failure。
- **Agentic token budget 同源**：`AgenticTokenBudgetServiceImpl` 不再依赖旧 `AGENT_SYSTEM_PROMPT` 常量；LangFuse 热更新、fallback、画像块开启/关闭时，预算估算不低估实际 system prompt token。
- **prompt↔trace 关联**：复制模型把 `langfuse.observation.prompt.name/version`(整数) 打到 generation span 后，LangFuse UI 是否真建立 generation↔prompt-version 关联（OTLP 路径无官方文档、GitHub issue #6973「span level prompt linking」仍 open）；不成则降级 metadata。
- **version 整数注入**：现有 `TracingContextHolder` 是 `Map<String,String>`，复制时 `prompt.version` 需走 `setAttribute(key, long)` 重载（小扩展 holder 支持类型化值，或对该 key 特判）。
- **generation span 识别**：确认 Spring AI 的 `gen_ai.*` span 经 LangFuse OTLP 摄取被识别为 `observation.type=generation`（否则 prompt 属性被忽略）。
- **跨线程传播**：Agentic 多跳 + reactor/工具线程下，holder 经 context-propagation 是否正确带到内部 generation span（搭车 ADR-015 既有待验项）。

**功能手测**：热更新（改 LangFuse → ≤60s 生效）；`KB_PROMPT_ENABLED=false` 走 fallback；LangFuse 停掉 → 旧缓存兜底、问答无感；应用启动预热失败不阻塞启动；业务首次访问 cold miss 时 LangFuse 不可用 → 等待一次 SDK timeout 后落代码 fallback + WARN；Agentic 预算与实际 system prompt 同源；缺变量 fail-fast；`staging` label 预发隔离。
