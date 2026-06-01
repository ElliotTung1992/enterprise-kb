---
created: 2026-05-18
tags: [feature, customer-assistant, intent-routing, domain-router, two-tier]
---

# 功能：客服助手意图识别 — 两层域路由

## 概述

客服助手原本用单个 ReactAgent + 3 个工具，意图识别靠 system prompt 隐式完成，无显式分类器、无评估指标。随着后续接入更多业务域，单 Agent 隐式路由会因 prompt 膨胀、工具描述互相干扰而错调率上升。

两层域路由把意图识别拆成两层，使其首次**可度量、可扩展**：

- **Tier-1 域路由**：把用户消息分类到一个业务域。新增组件，是头号风险点。
- **Tier-2 域内工具选择**：每域一个 `DomainHandler` 子 Agent，只面对自己的 3~7 个工具，复杂度恒定。

准确率归属彻底分离：路由器对"域"负责，域 Agent 对"工具"负责。架构决策见 [[decisions/adr-008-intent-routing-two-tier]]。

## 流水线

```
用户消息
   │
   ▼
攻击守卫 AttackGuardService ──命中──→ 拒绝话术
   │ 放行
   ▼
ConversationStateStore.load（Redis 路由状态）
   │
   ├─ 上轮 awaiting_slot=true ──→ 跳过路由（硬规则），沿用 current_domain
   │
   └─ 否则 ──→ DomainRouterService.route（一次路由模型调用，默认 LLAMA_CPP）
                     │
                     ▼
              RoutingDecision{primaryDomain, secondary[], runnerUp, evidence}
   │
   ▼
按 primaryDomain 分派
   ├─ AFTER_SALES / COMPLAINT ──→ DomainHandler.handle()
   ├─ HANDOFF                 ──→ 转人工话术
   ├─ CHITCHAT                ──→ 礼貌挡回话术
   └─ UNCLEAR                 ──→ 反问澄清话术
   │
   ▼
主动反问 ──→ 次要意图非空 / 情绪累积达阈值时附加反问，并写入 pendingOffer
   │
   ▼
更新 ConversationState + 持久化（customer_messages.domain 记录路由域）
```

## 关键组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `CustomerAssistantServiceImpl` | `kb-search/service/impl/` | 流水线编排，`chat()` 按 kill-switch 分 `routedChat` / `legacyChat` |
| `AttackGuardService(+Impl)` | `kb-search/service` `/impl` | 注入/越狱前置守卫（MVP 纯规则） |
| `DomainRouterService(+Impl)` | `kb-search/service` `/impl` | Tier-1 域分类，一次便宜模型调用 |
| `ConversationStateStore` | `kb-search/ai/` | Redis 路由状态读写 |
| `DomainHandler` | `kb-search/service/` | Tier-2 域处理器接口 |
| `AfterSalesDomainHandler` | `kb-search/service/impl/` | 售后域：ReactAgent + 2 工具 + HITL Hook |
| `ComplaintDomainHandler` | `kb-search/service/impl/` | 投诉域：ReactAgent + `escalateComplaint`，触发投诉 StateGraph |
| `Domain` | `kb-common/constants/` | 路由器输出空间枚举 |

## 路由器输出空间

`Domain` 枚举 = 已建业务域 + 三个特殊出口：

| 取值 | 含义 | 动作 |
|------|------|------|
| `AFTER_SALES` | 售后域 | 分派 `AfterSalesDomainHandler` |
| `COMPLAINT` | 投诉域 | 分派 `ComplaintDomainHandler` |
| `HANDOFF` | 业务内但域未接入 | 转人工坐席 |
| `CHITCHAT` | 真域外闲聊 | 礼貌挡回 |
| `UNCLEAR` | 信息不足 | 反问澄清 |

攻击/注入不进路由器，由前置 `AttackGuardService` 拦截，与域分类解耦。

## 路由器机制

- **模型**：路由是 5~13 路轻量分类，用便宜快模型（默认 `LLAMA_CPP`，配置 `router-provider`）。
- **输出格式**：单行管道 `PRIMARY|SECONDARY_CSV|RUNNER_UP|EVIDENCE|EMOTION`，与本项目既有 LLM 结构化输出风格（`ComplaintResponsibilityInferenceServiceImpl`）一致，不依赖在 OpenAI 兼容接口上不稳定的 JSON Schema 模式。`EMOTION` 标记本轮是否为纯情绪宣泄。
- **证据判 UNCLEAR**：要求路由器摘出用户原话证据；摘不出 → `UNCLEAR`。不信 LLM 自报置信度。
- **降级**：任何解析失败或调用异常都降级为 `UNCLEAR`——误判域代价远高于多反问一轮。
- **few-shot**：内联手写，刻意不与冻结测试集重叠（防污染）。

## 多轮路由与硬规则

每轮都重新路由，路由器输入含最近 N 轮上下文（`router-context-turns`，默认 3）+ 当前域。

**awaiting_slot 硬规则（精细化）**：若上一轮 `DomainHandler` 在向用户索要订单号，`ConversationState.awaitingSlot` 置 true，**仅当下一轮消息看起来像"裸槽位回填"时才跳过 Tier-1 路由**。`CustomerAssistantServiceImpl.looksLikeBareSlotFill()` 实现该启发：纯可打印 ASCII、长度 ≤ 30、至少含一个字母或数字。命中即直接回当前域；否则——尤其是含中文的自然语言（"算了不退了，要投诉"这类意图漂移）——走 Tier-1 路由重判。`DomainResult.of()` 用启发式正则识别"索要订单号"——只锁这一类反问，开放反问（"是否提交申请？"）不锁。

`DomainRouterService` 的 prompt 上下文也会带上 `awaiting_slot=true` 标记，配合 system prompt 中"等待槽位回填"规则，让路由器能识别含中文的槽位补充（如"我的订单号是 SO123"维持当前域）与明确意图漂移（"算了不退了，要投诉"按漂移切域）。

**pendingOffer（待确认提议）**：当本轮附加了主动反问（次要意图反问、或情绪累积升级反问），把被提议的业务域写入 `ConversationState.pendingOffer`。下一轮该字段作为**路由器上下文**喂回路由器（不走"跳过路由"），由 LLM 结合"是/好/不用了"等回应自行判定接受或拒绝——比写死的肯定词正则更鲁棒。

## 情绪累积升级

情绪与诉求分离：路由器输出 `EMOTION` 标记本轮是否纯情绪宣泄。`routedChat` 累计 `emotionStrikes` —— 纯情绪 +1，进入投诉域归零。累计达阈值（`EMOTION_STRIKE_THRESHOLD`，默认 3）时，即使用户从未明确提诉求，也主动反问"需要我帮您升级为正式投诉吗"，并把 `pendingOffer` 置为 `COMPLAINT`、计数归零。这实现 ADR-008 决策 5：情绪不切域，但累积到一定程度要主动兜住。

## DomainHandler 接口

每个业务域提供一个 `DomainHandler` 实现，域内编排（ReactAgent / StateGraph）对路由器完全黑盒。新增业务域 = 新增一个实现并注册，路由框架与流水线不动。

- `AfterSalesDomainHandler`：包 ReactAgent + `checkAfterSalesEligibility` + `submitAfterSalesReview` + `HumanInTheLoopHook`。HITL 中断写 `review_requests`，审核完成由 `resume()` 恢复 Agent。checkpoint `threadId = sessionId:after-sales`。
- `ComplaintDomainHandler`：包 ReactAgent + `escalateComplaint` 工具，信息齐全后触发投诉升级 StateGraph（[[features/complaint-escalation]] 一行未改）。checkpoint `threadId = sessionId:complaint`。

## 会话路由状态

`ConversationStateStore` 在 Redis `qa:state:{sessionId}` 存 `ConversationState`：

| 字段 | 含义 |
|------|------|
| `currentDomain` | 当前所处业务域 |
| `awaitingSlot` | 上轮是否在索要槽位（true 则下轮跳过路由） |
| `emotionStrikes` | 累积纯情绪信号次数（达阈值主动反问是否升级投诉，反问后归零） |
| `pendingOffer` | 上轮主动反问"是否需要处理"的业务域，作为下轮路由器上下文 |

TTL 24 小时，与对话历史一致。职责边界：Redis 存易变路由态，PostgreSQL `customer_messages.domain` 列存持久审计轨迹（域级准确率汇总用）。

## 评估

意图识别准确性必须可度量。评估资产在 `kb-search/src/test/resources/intent-eval/`：

- `frozen-testset.jsonl`：只用于打分，**永不进 prompt、永不修改**。
- `fewshot-pool.jsonl`：可作为 few-shot 进 prompt，线上误判捞回后补进这里。
- **硬切分防 few-shot 污染**：同一用户表达不可同时出现在两份文件，否则准确率虚高、"优化"产生虚假进步感。
- 评估单元是**多轮对话轨迹**，逐 user 轮标 `domain`；`SKIP` 标签表示该轮命中 awaiting_slot 硬规则，不计入路由器准确率。
- `DomainRouterEvalTest`（环境门控 `INTENT_EVAL=true`）跑真实路由器，输出域级混淆矩阵 + 记分卡。

**目标记分卡**：误判域率 < 3%、UNCLEAR 率 < 15%、HANDOFF 漏判率 < 5%。

## 配置开关

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `enterprise.kb.customer-assistant.routing-enabled` | `false` | kill-switch：true 走两层路由，false 回退旧单体路径 |
| `enterprise.kb.customer-assistant.shadow-routing-enabled` | `true` | 影子模式：旁路跑路由并打 `【SHADOW】` 日志，不影响响应 |
| `enterprise.kb.ai.router-provider` | `LLAMA_CPP` | Tier-1 路由调用的模型提供商 |
| `enterprise.kb.ai.router-context-turns` | `3` | 喂给路由器的上下文轮数 |

## 数据库

| 变更 | 迁移文件 | 说明 |
|------|---------|------|
| `customer_messages.domain` 列 | `024-add-customer-messages-domain.sql` | 记录每条消息被路由到的域，服务于域级准确率汇总与生产遥测 |

## 上线策略

分 4 阶段，先影子后灰度（详见 [[decisions/adr-008-intent-routing-two-tier]]）：

- **Phase 0**：运营测试同学按配额标注评估集（门控，先于一切）。
- **Phase 1**：路由器 + 守卫 + 迁移 + 评估 harness + 影子模式。
- **Phase 2**：抽取 `DomainHandler`（机械重构，行为不变）。
- **Phase 3**：接线切换，`routing-enabled` 作临时 kill-switch。

## 实现状态

- Phase 0–3 代码全部实现，全项目编译通过，93 个单元测试通过。
- ADR-008 全部决策已落地，含决策 5（情绪累积升级）与决策 6（次要意图延续，pendingOffer 机制）——这两项曾一度只设计未实现，现已补齐。
- `routing-enabled` 默认 **false**，生产仍走旧单体路径，行为不变。
- 未做（非代码 / 需基础设施）：评估集为模板（待运营测试同学填充）、影子数据为空（待部署）、迁移未落库、端到端与 HITL 回归未跑；`checkAfterSalesEligibility` 仍是 mock（待接 simple-shop）；`HANDOFF` 仅返回话术、无真实转人工对接（grill 时已 descope）。
- 推荐下一步：标注评估集 → 跑 `DomainRouterEvalTest` 达标 → 部署攒影子数据 → 影子达标后再开 `routing-enabled` 灰度。

## 与旧单体路径的对比

| 维度 | 旧单体（legacyChat） | 两层路由（routedChat） |
|------|---------------------|----------------------|
| 意图识别 | 单 ReactAgent system prompt 隐式 | 显式 `DomainRouterService` |
| 工具数 | 1 个 Agent 挂 3 工具 | 每域 Agent 仅 2~3 工具 |
| 可度量 | 无指标 | 域级混淆矩阵 + 记分卡 |
| 扩展 | 加工具 = 改 prompt，互相干扰 | 加域 = 新增 `DomainHandler` 注册 |
| 域外/转人工 | 无 | `HANDOFF` / `CHITCHAT` / `UNCLEAR` 显式出口 |

## 相关页面

- [[decisions/adr-008-intent-routing-two-tier]] — 两层域路由架构决策（含 grill-me 的 11 个决策点）
- [[features/hitl-after-sales]] — 售后审核（被 `AfterSalesDomainHandler` 包入）
- [[features/complaint-escalation]] — 投诉升级（被 `ComplaintDomainHandler` 包入）
- [[decisions/adr-006-customer-assistant-separation]] — 客服助手分离（本架构重构其内部结构）
