# 客服助手意图识别 — 两层域路由实施计划

## Context

商城客服助手 `CustomerAssistantServiceImpl` 当前用**单个 ReactAgent + 3 个工具**,意图识别完全靠 system prompt 隐式完成,无显式分类器、无评估指标。随着后续接入更多业务域(12 个月内预估 5~10 个域、每域 3~7 工具),单 Agent 隐式路由会崩溃:prompt 膨胀、工具描述互相干扰、错调率上升、无置信度信号、无法调试。

本计划落地 [[wiki/decisions/adr-008-intent-routing-two-tier]] 的设计共识:重构为**两层域路由架构**——Tier 1 域路由器(新增,头号风险点)+ Tier 2 各域子 Agent(`DomainHandler` 接口)。目标:意图识别首次可度量,误判域率 < 3%、UNCLEAR < 15%、HANDOFF 漏判 < 5%。

最终流水线:`攻击守卫 → DomainRouterService → DomainHandler 分派 → clarification/HANDOFF/secondary 处理`。

**关键约束**:
- 评估集(Phase 0)门控一切,先于任何路由器代码。
- 上线先影子后灰度,Phase 3 切换由影子达标 gate 控制。
- Tier-1 路由复用 MINIMAX(已是 highspeed 变体),加 `router-provider` 配置项。
- 投诉域已验证可干净剥离(`escalateComplaint` 只吃 `userId/orderId/description`,无隐藏耦合)。

---

## Phase 0 — 评估集(门控,先于路由器代码)

**人工**(运营测试同学):从 `customer_messages` 按 session 配额采样 80~120 段**完整多轮对话**并逐轮标注 `domain` 标签。配额必须覆盖:4 类域外、多轮槽位回填、意图漂移、同句复合、纯情绪无诉求。

**代码交付**:
- `kb-search/src/test/resources/intent-eval/` 下两份 jsonl:`fewshot-pool.jsonl`(可进 prompt)与 `frozen-testset.jsonl`(永不进 prompt、永不变)。**硬切分,防 few-shot 污染**。
- jsonl schema:每条 = 一段对话,`turns[]`,每轮含 `role / content / expected_domain`(取值 `AFTER_SALES / COMPLAINT / HANDOFF / CHITCHAT / UNCLEAR / SKIP`)。
- 采样 SQL(放 `docs/` 或脚本):按 session 拉取 `customer_messages`,辅助人工配额采样。

> 评估 harness 代码在 Phase 1 与路由器一起建(依赖 `DomainRouterService`)。

---

## Phase 1 — 路由器 + 守卫 + 基建 + 影子模式

### 新增文件(kb-search)

| 文件 | 职责 |
|------|------|
| `model/Domain.java` | 枚举:`AFTER_SALES / COMPLAINT / HANDOFF / CHITCHAT / UNCLEAR`,可扩展 |
| `dto/RoutingDecision.java` | record:`primaryDomain, List<Domain> secondary, runnerUp, evidence` |
| `service/DomainRouterService.java` | 接口:`route(history, ConversationState, message) → RoutingDecision` |
| `service/impl/DomainRouterServiceImpl.java` | 一次 MINIMAX 调用;prompt 含域描述 + few-shot;输出证据判 UNCLEAR(ADR 决策 7) |
| `service/AttackGuardService.java` + impl | 规则/关键词预过滤(注入、角色扮演);MVP 仅规则,小模型守卫后置 |
| `ai/ConversationStateStore.java` | Redis 存 `current_domain` + `awaiting_slot`;**复用 `RedisChatMemory` 的 StringRedisTemplate + ObjectMapper + 24h TTL 模式**,key `qa:state:{sessionId}` |

### 改动文件

- `ai/AiModelConfig.java` — 加配置项 `enterprise.kb.ai.router-provider`(默认 `MINIMAX`)。
- `db/changelog/024-add-customer-messages-domain.sql` — `ALTER TABLE customer_messages ADD COLUMN domain VARCHAR(32)`,注释说明;注册进 `db.changelog-master.xml`(下一编号 024,格式见 `023-add-complaint-plan-next-check-at.sql`)。
- `mapper/CustomerMessageMapper.java` + `.xml` — `insert` 增加 `domain` 参数,INSERT/SELECT 同步扩展。
- `CustomerAssistantServiceImpl.chat()` — **挂影子模式**:对每条真实流量旁路调一次 `DomainRouterService`,`log.info` 记录判定结果与老 Agent 实际工具调用对照,**不影响用户响应路径**。

### 评估 harness

- `kb-search/src/test/.../DomainRouterEvalTest.java` — 读 `frozen-testset.jsonl`,逐轮跑 `DomainRouterServiceImpl`,输出域级混淆矩阵 + 记分卡(误判域率 / UNCLEAR 率 / HANDOFF 漏判率)。
- `DomainRouterServiceImplTest.java` — 单测,**复用现有 mock 模式**:`@Mock ModelProviderResolver` 桩 ChatClient(参考 `ComplaintExecutorServiceImplTest`)。

**Phase 1 出口**:冻结测试集离线达标(误判域率 < 3%)→ 影子模式上线,攒数据。

---

## Phase 2 — DomainHandler 抽取(机械重构,行为不变)

| 文件 | 职责 |
|------|------|
| `service/DomainHandler.java` | 接口:`Domain domain()` + `CustomerAssistantResponse handle(...)` + 上报 `awaiting_slot` |
| `service/impl/AfterSalesDomainHandler.java` | 包现有 ReactAgent + `checkAfterSalesEligibility` / `submitAfterSalesReview` + `HumanInTheLoopHook`;HITL 中断处理(`processHitlInterrupt`)与 `resumeWithFeedback` **迁入此类** |
| `service/impl/ComplaintDomainHandler.java` | 包 `ComplaintEscalationService.createComplaint` + `ComplaintWorkflowService.startPlanning`,**投诉 StateGraph 一行不改** |

- Handler 注册:Spring 自动收集所有 `DomainHandler` bean → `Map<Domain, DomainHandler>`,加域 = 新实现 + 注册,路由框架不动。
- `ReviewFeedbackHelper`、`ReviewRequestService`、`ComplaintController` 保持不变;HITL resume 路径改为分派到 `AfterSalesDomainHandler`。

**Phase 2 出口**:抽取后行为对齐老路径(现有 `CustomerAssistantServiceImplTest` 适配后通过)。

---

## Phase 3 — 接线切换 + 临时 kill-switch

- `CustomerAssistantServiceImpl.chat()` 重构成薄流水线:
  1. `AttackGuardService` 拦截 → 命中即拒。
  2. 查 `ConversationStateStore`:上轮 `awaiting_slot` 为真 → **跳过路由**,直接分派当前域 Handler(ADR 决策 4 硬规则)。
  3. 否则 `DomainRouterService.route(...)` → `RoutingDecision`。
  4. 按 `primaryDomain` 分派:`UNCLEAR` → 反问;`HANDOFF` → 转人工;`CHITCHAT` → 礼貌挡回;域 → 对应 `DomainHandler`。
  5. 写 `current_domain`、`customer_messages.domain`;`secondary[]` 非空 → 主域处理完反问次要意图。
- `resumeWithFeedback` 入口保留,直达 `AfterSalesDomainHandler`(resume 不经路由器)。
- **Kill-switch**:配置 `enterprise.kb.customer-assistant.routing-enabled`,为 `false` 时走老单体 Agent;老路径保留**一个版本周期**,稳定后删除。
- **灰度**:配置百分比,X% 流量走新流水线,盯记分卡逐步放量。
- 迁移注意:部署时进行中会话无 `current_domain`,按"为空即首轮、重新路由"处理。

**Phase 3 出口**:影子达标 → 灰度全量 → 下周期删 kill-switch 与老路径。

---

## 复用的现有能力

- `ModelProviderResolver.resolveChatClient(provider)` — kb-search/.../ai/ModelProviderResolver.java:44 — 取 MINIMAX ChatClient。
- `RedisChatMemory` — kb-search/.../ai/RedisChatMemory.java — `ConversationStateStore` 的实现模板(同 RedisTemplate/ObjectMapper/TTL)。
- `TransactionTemplate` 私有方法事务模式 — `CustomerAssistantServiceImpl.persistExchange` — 持久化保持此写法。
- HITL 链路 `ReviewFeedbackHelper` / `ReviewRequestService` / `ReviewRequestMapper` — 不变,迁入 `AfterSalesDomainHandler`。
- 测试 mock 模式 — `ComplaintExecutorServiceImplTest`(桩 `ModelProviderResolver`)、`CustomerAssistantServiceImplTest`(桩 mapper)。

## 关键改动文件清单

- 新增:`Domain` / `RoutingDecision` / `DomainRouterService(+Impl)` / `AttackGuardService(+Impl)` / `ConversationStateStore` / `DomainHandler` / `AfterSalesDomainHandler` / `ComplaintDomainHandler`
- 改动:`CustomerAssistantServiceImpl`、`CustomerAssistantController`(resume 分派)、`AiModelConfig`、`CustomerMessageMapper(+.xml)`
- 迁移:`kb-app/src/main/resources/db/changelog/024-add-customer-messages-domain.sql` + `db.changelog-master.xml`
- 测试/数据:`DomainRouterServiceImplTest`、`DomainRouterEvalTest`、`intent-eval/*.jsonl`

---

## Verification

构建(必须 JDK 21):
```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home
mvn install -pl kb-search -am -DskipTests
```

1. **单测**:`mvn test -pl kb-search` — `DomainRouterServiceImplTest`、适配后的 `CustomerAssistantServiceImplTest` 全绿。
2. **评估记分卡**:跑 `DomainRouterEvalTest` 对冻结测试集,确认误判域率 < 3%、UNCLEAR < 15%、HANDOFF 漏判 < 5%——不达标不进影子。
3. **迁移**:启动应用,确认 Liquibase 应用 024,`customer_messages.domain` 列存在。
4. **影子模式**:部署后查日志,确认每条流量有旁路路由判定记录,且用户响应仍走老路径不受影响。
5. **手工端到端**(`POST /api/v1/after-sales/ask`):逐个验证——售后退款、投诉升级、域外闲聊、业务内域未建(HANDOFF)、多轮槽位回填("JD12345" 不被重路由)、对话中途意图漂移(售后→投诉)、纯情绪不误升级。
6. **HITL 回归**:`submitAfterSalesReview` 触发审核 → `/reviews/{id}/approve` → `resumeWithFeedback` 正常恢复。
7. **kill-switch**:`routing-enabled=false` 时回退老 Agent,行为与重构前一致。
