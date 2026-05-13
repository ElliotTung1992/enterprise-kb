---
created: 2026-05-13
tags: [hitl, spring-ai-alibaba, react-agent, checkpoint]
---

# HumanInTheLoopHook

## 概念

`HumanInTheLoopHook` 是 Spring AI Alibaba Graph 框架提供的 `afterModel` 钩子，在 LLM 输出工具调用请求后、实际执行工具前拦截，暂停 Agent 执行并等待人工决策。

```
LLM 决定调用工具
    │
    ▼ afterModel hook
    HumanInTheLoopHook 拦截
    ├─ 持久化图状态（CheckpointSaver → Redis）
    ├─ 写 review_requests（status=PENDING）
    └─ 返回"审核中"响应给用户

    [审核员决策]

    resumeWithFeedback(sessionId, feedback)
    └─► Agent 从 checkpoint 恢复执行
```

## 三种决策

| 决策 | 效果 |
|------|------|
| `APPROVED` | 工具按原参数执行 |
| `REJECTED` | 工具不执行，LLM 收到拒绝原因 |
| Edit | 修改参数后执行（本项目未启用） |

## 配置方式

```java
HumanInTheLoopHook hook = HumanInTheLoopHook.builder()
    .toolNames("submitAfterSalesReview")
    .checkpointSaver(checkpointSaver)
    .build();

ReactAgent reactAgent = ReactAgent.builder()
    ...
    .compileConfig(CompileConfig.builder()
        .recursionLimit(MAX_RECURSION_LIMIT)
        .hook(hook)
        .build())
    .build();
```

## CheckpointSaver（Redis）

Agent 暂停时，`RedisSaver` 将完整图状态（消息列表、中间步骤）序列化到 Redis：

- 依赖：`org.redisson:redisson:3.22.0`
- Bean：`AgentCheckpointConfig.java` 中配置 `RedissonClient` + `RedisSaver`
- Key 策略：`threadId = sessionId.toString()`
- 连接池：`connectionPoolSize=4, connectionMinimumIdleSize=1`（保守配置）
- 建议 TTL：7 天（与业务 SLA 对齐）

## 恢复流程

```java
// 审核员批准时
CustomerAssistantResponse response = customerAssistantService.resumeWithFeedback(
    reviewReq.getSessionId(),
    ReviewFeedbackHelper.buildFeedback(reviewReq, FeedbackResult.APPROVED)
);
// LLM 调用成功后再提交 DB
reviewRequestService.approve(id, reviewerId, comment);
```

> [!warning] 事务安全
> LLM 调用（resumeWithFeedback）必须在 DB 提交（approve/reject）之前完成。若 LLM 失败，DB 不更新，申请保持 PENDING 可重试。见 [[decisions/adr-005-hitl-transaction-ordering]]

## 版本兼容性

- 当前项目版本：`spring-ai-alibaba` 1.1.2.1
- 中断异常：`SubGraphInterruptionException`（捕获后写 review_requests 并返回"审核中"）

## 相关页面

- [[features/hitl-after-sales]] — 完整业务流程
- [[decisions/adr-005-hitl-transaction-ordering]] — 事务顺序
- [[ai-rag/agentic-qa]] — ReactAgent 基础
