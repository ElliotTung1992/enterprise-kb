---
created: 2026-05-13
tags: [feature, hitl, after-sales, customer-assistant]
---

# 功能：HITL 售后审核 & 商城客服助手

## 概述

商城客服助手是一套独立于知识库问答的 AI 驱动售后系统，集成了 **Human-in-the-Loop（HITL）** 审核流程。

核心能力：
- AI 对话引导用户完成售后申请（退款/换货）
- 自动查询订单资格
- 触发 HITL 拦截，提交人工审核
- 审核员批准/拒绝后，Agent 恢复执行并通知用户

## 完整业务流程

```
用户: "我要退款"
    └─► CustomerAssistantService (ReactAgent, threadId=sessionId)
            AI: "请提供您的订单号"
            用户: "ORDER-12345"
            Tool: checkAfterSalesEligibility(orderId)
                └─► 查询 simple-shop API → 判断是否符合退款条件
            Tool: submitAfterSalesReview(orderId, reason, orderDetails)
                └─► HumanInTheLoopHook 拦截（afterModel 阶段）
                    写 review_requests (status=PENDING)
                    Checkpoint → Redis（保存图状态）
                    返回: "您的申请已提交，审核中"

审核员侧:
    GET /api/v1/after-sales/reviews?status=PENDING
    POST /api/v1/after-sales/reviews/{id}/approve
        └─► resumeWithFeedback() → Agent 恢复 → 生成最终回复
            写 customer_messages: "申请已通过，退款 3-5 工作日内到账"

用户侧:
    GET /api/v1/after-sales/sessions/{id}/messages
    └─► 看到审核结果
```

## 与知识库问答的关系

商城客服助手与知识库 QA **完全独立**：

| 维度 | 知识库 QA | 商城客服助手 |
|------|-----------|------------|
| 数据表 | qa_sessions / qa_messages | customer_sessions / customer_messages |
| 服务 | AgenticQnAServiceImpl | CustomerAssistantServiceImpl |
| 控制器 | QnAController | CustomerAssistantController |
| 路由前缀 | /api/v1/spaces/{spaceId}/qa | /api/v1/after-sales |
| 知识空间绑定 | 必须 | 无（spaceId = null） |
| HITL | 无 | 有（submitAfterSalesReview） |
| 前端入口 | /dashboard.html → qa.html | /customer.html |

> [!key-insight] 分离原则
> HITL 工具和售后逻辑必须从 AgenticQnAServiceImpl 中彻底剥离，避免知识库用户触发售后流程。见 [[decisions/adr-006-customer-assistant-separation]]

## 关键组件

| 组件 | 路径 | 职责 |
|------|------|------|
| `CustomerAssistantService` | `kb-search/service/CustomerAssistantService.java` | 对话、会话管理、HITL 恢复 |
| `CustomerAssistantController` | `kb-search/controller/CustomerAssistantController.java` | 用户对话 + 审核员 API |
| `ReviewRequestService` | `kb-search/service/ReviewRequestService.java` | 审核申请 CRUD |
| `AgentCheckpointConfig` | `kb-search/ai/AgentCheckpointConfig.java` | RedisSaver bean 配置 |

## HITL 事务安全

审批/拒绝时必须先完成 LLM 调用（`resumeWithFeedback`），成功后再提交 DB 变更（`approve`/`reject`）。

见 [[decisions/adr-005-hitl-transaction-ordering]]

## 相关页面

- [[ai-rag/hitl-hook]] — HumanInTheLoopHook 机制详解
- [[api/after-sales]] — API 端点参考
- [[database/entities/after-sales-tables]] — 数据表结构
- [[decisions/adr-005-hitl-transaction-ordering]] — 事务顺序决策
- [[decisions/adr-006-customer-assistant-separation]] — 商城与知识库分离决策
