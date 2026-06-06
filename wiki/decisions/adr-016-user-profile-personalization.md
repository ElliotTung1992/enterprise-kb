---
id: adr-016
title: 用户画像与个性化问答
status: Accepted
date: 2026-06-05
tags: [decision, personalization, profile, kafka, rag]
---

# ADR-016：用户画像与个性化问答

## 状态

Accepted（2026-06-05，经 grill-me 收敛）。代码已落地：Phase 1 在线可用；Phase 2 离线推断需起 Kafka 联调验证。

## 背景

希望为 KB 问答引入"用户画像"以做个性化。但本系统的现实约束很强：
画像对象是**内部员工**，问答是**任务驱动**（同一人不同时间问不同问题），且 schema 里
**没有人口属性 / 订单 / 商品 / 偏好数据**——唯一信号是对话行为 + 身份/权限。
naive 的"行为画像"在此地基不稳。

## 决策

把"关于用户的信息"按**变化的时间尺度**分三层，只有"以月计变化"的稳定属性才进画像：
持久画像（本 ADR） / 会话短期记忆（已有 Redis） / 每次 query 意图（路由/显式 space）。

1. **画像对象 = 内部员工**（非商城客户）。
2. **作用通道 = 仅 prompt 侧**；检索个性化 deferred。理由：单次 query 已是最强意图，长期画像加权检索易"个性化到答错"；且 `SearchHit` 不带向量，检索加成成本/风险更高。
3. **V1 维度 = 资历/角色 + 答案偏好（长度/语言/风格）**。
4. **砍掉"主题兴趣"与"常用 space"**：主题随时间漂移属会话层，space 属意图层——都不是"人的稳定属性"。
5. **取值 = 显式优先 + 推断回落**；字段级来源（declared/inferred 分列于 JSONB）。
6. **显式同步写（即时生效）+ 推断异步写（不覆盖显式）**。
7. **离线统一推断 4 维**：一次 LLM 从近期消息推断 资历/长度/语言/风格（各带置信度），输出严格枚举（不产自由文本人设）；`ProfileService.recordInference` 逐字段过置信闸写入 inferred（达标才更新、否则保留旧值，防低置信抖动）。declared 仍优先。
8. **架构 = 离线算 + 在线读**，单一存储 `user_profiles`，**JSONB 单列**，枚举校验在 `ProfileService` 写入时把关。
9. **不单开模块，落 kb-user**：单开 `kb-profile` 会因 worker 依赖 kb-search 的 qa_messages+LLM 而与 kb-search 循环依赖。
10. **V1 不上缓存，直读 DB**：画像读=每问一次 PK 查、被 LLM 秒级延迟淹没，缓存失效维护不值（defer Phase 3）。
11. **MQ = Kafka**（项目级基础设施投资）；producer 经 Spring 事件 + `@TransactionalEventListener(AFTER_COMMIT)` 解耦，Kafka 发送隔离在 `@ConditionalOnProperty` 监听器，**Phase 2 默认关、不依赖 Kafka 启动**。
12. **冲突裁决 = 软默认 + LLM 裁决**：画像以"默认偏好"注入，提示词写明"本轮明确要求优先"。优先级阶梯：本轮指令 > 会话 > 显式画像 > 推断画像 > 系统默认。**一次性**指令（"这次详细点"）由 LLM 化解、不入画像；**持久**指令（"以后…"）的跨会话生效由统一推断兜底（决策 15）。
13. **应用范围 = 标准 QA + Agentic QA 都注入**。
14. **Kafka 降级线程池**：投递失败（同步异常 / 异步 future 失败）转本地有界 `ThreadPoolTaskExecutor`（core1/max2/queue200，满则丢弃）直接跑共享 `ProfileInferenceRunner`，Kafka 故障下退化为「单机异步」；producer `max.block.ms=3000` 快速失败避免阻塞请求线程；runner 顶层吞异常防毒丸消息。worker 与降级路径共用 runner（去抖天然幂等）。
15. **答案偏好与资历统一走推断（取代早先的"对话内偏好捕获"）**：答案偏好（长度/语言/风格）不再单独抽取写 declared，而是和资历**同走决策 7 的统一推断** → 写 inferred、逐字段过置信闸、declared 仍优先。用户明说的"以后用中文"被该次推断识别为高置信 inferred；本轮即时效果靠会话上下文（无回执）。**取消** `AnswerPreferenceCaptureService` / `mergeDeclaredPreference` / 事件携带消息——事件只带 userId，推断从近期消息读。理由：一套机制比两套简单，显式声明天然获高置信。（演进史：grill 先判"答案偏好显式 only"，后加同步对话捕获，再改异步捕获，最终统一进推断。）

## 后果

- **正**：方案地基稳、风险低（无检索回归）、Phase 1 可独立上线、Phase 2 默认关不增启动依赖、可扩展（JSONB + 字段级来源）。
- **负**：V1 画像较"薄"（本质是稳定偏好档案而非丰富行为画像）；引入 Kafka 增加运维面（仅 Phase 2 启用时）。

## 备选与放弃

- 检索个性化（兴趣向量/词项加权）：deferred 到 Phase 3。
- 单独 kb-profile 模块：放弃（循环依赖）；改落 kb-user。
- typed columns：放弃（选 JSONB 换扩展性，校验上移服务层）。
- Redis 缓存 / RocketMQ：缓存 deferred；MQ 选 Kafka（生态 + 与 ClickHouse 协同）。

## 待验证（运行态）

**Phase 1**（可直接验收）：迁移 034 落库；手测 设"简洁"→变短 / 设英文→英文 / 本轮"要详细"覆盖 / 关个性化→注入块消失。
**Phase 2**（起 Kafka 联调）：事件 AFTER_COMMIT 投递、worker 去抖/置信闸、`recordInference` 不覆盖 declared；**降级实测**（停 Kafka → 走线程池兜底、`max.block.ms` 不阻塞请求线程）；多实例消费组不重复消费。

**待讨论（重要）**：资历推断质量的验证与灰度——置信闸之外缺"上线前确认够准"的方法，建议参照 intent-routing 的影子 + 评估集（先记录不应用、抽样标注算准确率，达标再生效）。其余未决点（语言回落真相、会话级粘性、前端设置页、推断可观测）见设计稿 §14。
