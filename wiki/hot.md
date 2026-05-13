# Hot Cache

_Last updated: 2026-05-13_

## 最近工作焦点

**HITL 售后审核 + 商城客服助手**（Phase 1-6 已完成）

### 关键决策（本轮最重要）
1. **事务顺序**：`resumeWithFeedback`（LLM）必须先于 `approve/reject`（DB）执行 → [[decisions/adr-005-hitl-transaction-ordering]]
2. **完全分离**：商城客服从 `AgenticQnAServiceImpl` 彻底剥离，独立服务+独立表 → [[decisions/adr-006-customer-assistant-separation]]
3. **`@Transactional` 不拦截私有方法**：`CustomerAssistantServiceImpl` 中用 `TransactionTemplate.executeWithoutResult()` 替代

### 当前文件索引
| 概念 | 文件 |
|------|------|
| HITL 完整业务流程 | [[features/hitl-after-sales]] |
| HumanInTheLoopHook 机制 | [[ai-rag/hitl-hook]] |
| 售后 API 端点 | [[api/after-sales]] |
| 数据表结构 | [[database/entities/after-sales-tables]] |
| 事务顺序 ADR | [[decisions/adr-005-hitl-transaction-ordering]] |
| 分离决策 ADR | [[decisions/adr-006-customer-assistant-separation]] |

### 关键代码路径
- `CustomerAssistantController` → `CustomerAssistantService.chat()` → ReactAgent → HumanInTheLoopHook
- 审批路径：`findById → resumeWithFeedback → approve/reject`
- Checkpoint：Redis，`threadId = sessionId.toString()`，Redisson 连接池 4/1

### 未完成
- Phase 7：集成测试 & 联调（需启动完整基础设施）
