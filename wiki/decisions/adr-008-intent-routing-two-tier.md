---
created: 2026-05-18
tags: [adr, architecture, customer-assistant, intent-routing, domain-router]
---

# ADR-008：客服助手意图识别采用两层域路由架构

**状态**：Phase 0-3 全部实现、未上线（kill-switch `enterprise.kb.customer-assistant.routing-enabled` 默认 false，旧单体路径仍生效）

## 背景

商城客服助手（`CustomerAssistantServiceImpl`）当前用**单个 ReactAgent + 3 个工具**（`checkAfterSalesEligibility` / `submitAfterSalesReview` / `escalateComplaint`），意图识别完全交给 LLM 通过 system prompt 里的"判断条件"隐式完成，没有显式意图分类器，也没有意图准确率的评估指标。

随着后续接入更多业务域（预估 12 个月内 5~10 个域、每域 3~7 个工具，总工具数 15~70），单 Agent 隐式路由会崩溃：prompt 膨胀、工具描述互相干扰、错调率上升、无置信度信号、无法调试。

本 ADR 记录一次设计评审（grill-me）达成的共识。

## 决策

### 0. 评估口径：只评"域路由"

"意图识别正确" = **域路由正确**，单一口径。工具选择是各域 Agent 的内部职责，由各域自评，不属于本架构的"意图识别准确性"。两层准确率归属彻底分离：路由器对"域"负责，域 Agent 对"工具"负责。

### 1. 两层路由架构

- **Tier 1 域路由**：5~10 路分类。新增组件，是头号风险点。
- **Tier 2 域内工具选择**：每域一个子 Agent，只面对自己的 3~7 个工具，复杂度恒定。

"场景多了会乱"的乱源是 Tier 1，不是 Tier 2。所有设计精力集中在 Tier 1。

### 2. 路由器形态：普通前置服务 + `DomainHandler` 接口

新增 `DomainRouterService.route(history, state, message)` → `RoutingDecision{primaryDomain, secondary[], evidence}`。纯命令式服务，**不**做成 StateGraph 节点——域路由是"一次分类 + 一次分派"，不是图；纯服务可脱离图引擎直接喂评估集做单测。

子 Agent 全部收敛到 `DomainHandler` 接口（`domain()` + `handle(...)`）。售后域实现包 ReactAgent，投诉域实现直接包现有投诉升级 StateGraph（[[decisions/adr-007-complaint-escalation-stategraph]]，一行不改）。路由器只与接口打交道，对"域集合未知"免疫——加域 = 实现接口 + 注册，路由框架不动。

最终流水线：`攻击守卫 → DomainRouterService → 按 primaryDomain 选 DomainHandler → 委派 → 处理 clarification/HANDOFF/secondary 反问`。老 `CustomerAssistantServiceImpl` 重构成此薄流水线，不保留新老并存的双套意图逻辑。

路由是 5~13 路分类，用便宜快模型（默认本地 `LLAMA_CPP`，或 Qwen-Turbo 级），域内子 Agent 才用主力模型。

### 3. 路由器输出空间

`N 个已建域` + `HANDOFF`（业务内但域未建 → 转人工）+ `CHITCHAT`（真域外闲聊）+ `UNCLEAR`（信息不足 → 反问）。

`HANDOFF` 必须独立成类——持续扩张的系统永远有"该接而未接"的业务，把这类判成 `CHITCHAT` 拒掉等于赶走真实客户；转人工的 case 正好是下一个该建域的需求池。攻击/注入 (`忽略指令` 等) **不进路由器**，由前置守卫（规则 + 小模型）拦截，与域分类解耦。

### 4. 多轮路由：每轮带上下文重路由 + 裸槽位硬规则

每轮都重新路由，路由器输入含"最近 N 轮上下文 + 当前域 + awaiting_slot 标记"（起步 N=3，按评估集校准）。

**硬规则（精细化）**：若上一轮子 Agent 因缺槽位向用户反问（对话状态打了 `awaiting_slot` 标记），**仅当本轮消息看起来像"裸槽位回填"时才跳过 Tier 1 路由**——具体判定为：纯可打印 ASCII（字母/数字/标点/空格）、长度 ≤ 30、且至少含一个字母或数字。命中即跳过路由直接回当前域；否则——尤其是含中文的自然语言（"算了不退了，要投诉"这类意图漂移）——一律走 Tier-1 路由重判。这样既能保留"JD12345"裸槽位回填的快路径与成本优化，又不会把用户明确切域的诉求吞回当前域。

为辅助 Tier-1 路由器处理"含中文的槽位补充"（如"我的订单号是 SO123"）与歧义短回复（如"嗯""好的"），路由器 prompt 上下文会显式带上 `awaiting_slot=true` 标记，并附加规则与 few-shot：补槽位场景维持当前域，明确放弃原诉求并表达新诉求时按意图漂移切域。

### 5. 意图漂移：情绪 ≠ 切换信号

情绪（愤怒/讽刺）不切域，仅写入状态用于话术升温与累积证据；只有出现明确**诉求结构**才切域。同一 session 情绪信号累积到阈值（约 3 次），主动反问"需要升级为正式投诉吗"——用反问澄清替代自动升级。可行性依据：`escalateComplaint` 后接专员 HITL 审核，误升级有人工兜底，故可偏召回但不可自动升级。

### 6. 复合意图：主域 + 次要列表

路由器输出单个 `primaryDomain` + `secondary[]`（域列表）。主域处理完后主动反问次要意图，不静默丢弃。不上多意图并行编排——绝大多数"同句复合"是带情绪的假复合，编排引擎是过早优化。同句两域时，带明确诉求者优先；两者皆有诉求则投诉域优先。

### 7. 置信度：输出证据判 UNCLEAR，不信裸数字

LLM 自报置信度严重未校准，不可卡阈值。路由器输出 `primary` + `runner_up` + `evidence`（从用户原话摘出的判定依据）：摘不出具体证据、或主次域证据强度相当 → `UNCLEAR`。

区分"域不清"（→ `UNCLEAR`，路由器反问）与"域清楚但缺槽位"（→ 进子 Agent，由子 Agent 反问）——后者不是 `UNCLEAR`。

### 8. 对话状态存储

- `customer_messages` 加 `domain` 列：每条消息被路由到的域，**持久可查**，服务于域级汇总报表与评估。
- `current_domain` + `awaiting_slot` 放 Redis（与对话历史同 key 同 TTL）：易变的路由输入态。

切分原则：PG 存持久审计轨迹，Redis 存易变路由态。ReactAgent 的 `RedisSaver` checkpoint 是图引擎快照，不复用为业务状态。

## 评估

意图识别准确性必须可度量，否则重构无法判断好坏。

- **评估单元 = 多轮对话轨迹**，逐轮标注期望 `domain` 标签（N 域 / HANDOFF / CHITCHAT / UNCLEAR / SKIP-awaiting_slot）。孤立单句测不出多轮路由的 bug。
- **配额覆盖**：强制包含 4 类域外、多轮槽位回填、意图漂移、同句复合、纯情绪无诉求——决策 4~7 每条都要有对应 case 验证。
- **few-shot 污染切分**：标注池硬切为 `few-shot 池`（可进 prompt）与 `冻结测试集`（永不进 prompt、永不变）。两者重叠会让准确率虚高，且使"加 few-shot 优化"产生虚假进步感。
- **目标记分卡**：误判域率 < 3%、UNCLEAR 率 < 15%、HANDOFF 漏判率 < 5%。
- **初始集**：从 `customer_messages` 按 session 配额采样 80~120 段完整多轮对话，由运营/测试同学标注。
- **线上闭环**：`customer_messages.domain` 列提供生产遥测；线上误判捞回、人工确认后进 few-shot 池并据此重校准 UNCLEAR 边界。

## 实施阶段

| 阶段 | 内容 | 出口条件 |
|------|------|---------|
| **Phase 0** | 评估集：运营测试同学标注 80~120 段多轮对话，做 few-shot / 冻结测试集硬切分 | 门控一切，先于任何代码 |
| **Phase 1** | 只建 `DomainRouterService` + 前置攻击守卫 | 冻结测试集离线达标（误判域率 <3%）→ 挂影子模式跑线上流量，只记录不作数 |
| **Phase 2** | 抽取 `DomainHandler`：售后域包现有 ReactAgent，投诉域包现有投诉 StateGraph（机械重构，行为不变） | 行为对齐老路径 |
| **Phase 3** | 接线切换，路由器 → handler；老单体 Agent 保留一个版本周期作临时 kill-switch | 影子达标 → 灰度（canary）逐步放量 → 稳定后删除 kill-switch |

上线策略：**先影子后灰度**。临时 kill-switch 是一个版本周期的逃生开关，非永久双套逻辑。

迁移注意：部署时进行中的会话无 `current_domain`，按"为空即首轮、重新路由"处理，极小概率打断回填中会话，可接受。

## 后果

**优点**：
- 两层准确率归属分离，加域不增加单层复杂度
- 路由器为纯服务，可脱离图引擎单测，可一行换模型
- `DomainHandler` 接口对域集合未知免疫
- 意图识别首次可度量、有目标记分卡、有线上闭环

**权衡**：
- 多一跳路由 LLM 调用（便宜模型，~300ms）与两层失败点
- 影子模式有额外 LLM 调用成本与日志基建成本
- 评估集标注是一次性人力投入，且必须先于编码

## 相关决策

- [[decisions/adr-006-customer-assistant-separation]] — 客服助手从知识库问答分离（本架构重构其内部结构）
- [[decisions/adr-007-complaint-escalation-stategraph]] — 投诉升级 StateGraph（作为投诉域 `DomainHandler` 被原样包入）

## 相关功能

- [[features/hitl-after-sales]] — 售后审核（将成为售后域 `DomainHandler`）
- [[features/complaint-escalation]] — 投诉升级（将成为投诉域 `DomainHandler`）
