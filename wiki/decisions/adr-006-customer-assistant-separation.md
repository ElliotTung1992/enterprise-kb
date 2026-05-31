---
created: 2026-05-13
tags: [adr, architecture, customer-assistant, separation]
---

# ADR-006：商城客服助手从知识库问答中完全分离

**状态**：已采用

## 背景

HITL 售后工具（`checkAfterSalesEligibility`、`submitAfterSalesReview`）最初集成在当时的 `AgenticQnAServiceImpl` 中，与知识库检索工具（`searchKnowledgeBase`）共存于同一 ReactAgent。

问题：
- 知识库用户可能意外触发售后流程
- 售后会话需要独立的存储（无需绑定 space_id）
- HITL hook 和 checkpoint 配置污染了 Agentic RAG 路径
- 两个业务域的迭代频率和权限要求不同

## 决策

**彻底拆离，新建独立的商城客服模块：**

| 维度 | 知识库 QA | 商城客服助手 |
|------|-----------|------------|
| 服务类 | `MdAgenticQnAServiceImpl`（原 `AgenticQnAServiceImpl` 随迁移 031 退役） | `CustomerAssistantServiceImpl` |
| 数据表 | `qa_sessions / qa_messages` | `customer_sessions / customer_messages` |
| ReactAgent 工具 | `searchKnowledgeBase` + `readFullSection` | `checkAfterSalesEligibility` + `submitAfterSalesReview` |
| HITL Hook | 无 | `HumanInTheLoopHook` |
| 知识空间绑定 | 必须（VIEWER 权限） | 无（`space_id` nullable） |
| 前端入口 | `/dashboard.html` | `/customer.html` |

## `@Transactional` 私有方法问题

`CustomerAssistantServiceImpl` 中，`persistExchange` 和 `persistMessage` 是私有方法，Spring AOP 无法拦截私有方法上的 `@Transactional` 注解。

**解决方案**：注入 `TransactionTemplate`，在私有方法内显式调用 `executeWithoutResult()`：

```java
private void persistExchange(UUID sessionId, UUID userId, String question, String answer) {
    try {
        transactionTemplate.executeWithoutResult(status -> {
            ensureSessionExists(sessionId, userId, question);
            customerMessageMapper.insert(sessionId, "user", question);
            customerMessageMapper.insert(sessionId, "assistant", answer);
            customerSessionMapper.updateUpdatedAt(sessionId, Instant.now());
        });
    } catch (Exception e) {
        log.warn("持久化消息失败：sessionId={}", sessionId, e);
    }
}
```

## 前端分离

门户首页 `/index.html` 提供两张入口卡片：
- **企业知识库** → `/dashboard.html`
- **商城客服中心** → `/customer.html`

商城页面（`customer.html` / `reviews.html`）不再出现在知识库侧边栏中，知识库页面也不再有商城导航链接。

## 相关页面

- [[features/hitl-after-sales]] — 完整功能
- [[architecture/overview]] — 系统架构
