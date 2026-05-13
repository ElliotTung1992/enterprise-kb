---
created: 2026-05-13
tags: [adr, hitl, transaction, safety]
---

# ADR-005：HITL 审批的事务安全顺序

**状态**：已采用

## 背景

审核员批准/拒绝售后申请时，涉及两个操作：
1. **LLM 调用**：`resumeWithFeedback(sessionId, feedback)` — 恢复 ReactAgent，生成最终回复（外部不可回滚）
2. **DB 写入**：`reviewRequestService.approve/reject(id, ...)` — 将 `review_requests` 状态改为 `APPROVED/REJECTED`

错误顺序（先 DB 后 LLM）：
```
approve() → DB committed ✓
resumeWithFeedback() → LLM 超时 ✗
结果: DB 显示 APPROVED，但 Agent 未恢复，用户收不到结果
且申请已不可重试（状态已变）
```

## 决策

**先 LLM，再 DB。LLM 失败时 DB 不更新，申请保持 PENDING 可重试。**

```java
// 正确顺序（CustomerAssistantController）
ReviewRequest reviewReq = reviewRequestService.findById(id);
CustomerAssistantResponse response = customerAssistantService.resumeWithFeedback(
    reviewReq.getSessionId(), feedback);   // ① LLM 调用（外部）
reviewRequestService.approve(id, reviewerId, comment);  // ② DB 写入（仅在①成功后）
```

## 后果

- LLM 调用失败 → 抛异常上抛 → DB 不变 → 申请仍为 PENDING → 审核员可重试
- LLM 成功但 DB 失败 → 罕见，申请状态停留 PENDING，Agent 已恢复（重试批准会再次恢复 Agent，幂等性待补充）
- 这是一种"尽力而为"的 saga 模式，非严格两阶段提交

## 相关页面

- [[features/hitl-after-sales]] — 完整流程
- [[ai-rag/hitl-hook]] — HITL 机制
