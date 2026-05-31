# .planning/

工作流计划归档。每个日期目录对应一段独立工作流，含 `task_plan.md` + `progress.md` + `findings.md` 三件套，或单一的 `plan.md`，目录内 `README.md` 给出概览。

## 工作流目录

| 日期 | 主题 | 状态 | 关联 wiki |
|------|------|------|-----------|
| [2026-05-13](2026-05-13-hitl-after-sales/) | 售后 HITL | Phase 1-6 完成，Phase 7 集成测试待执行 | [[features/hitl-after-sales]] · [[decisions/adr-005-hitl-transaction-ordering]] |
| [2026-05-14](2026-05-14-complaint-escalation-design/) | 用户投诉升级（Plan + ReAct） | Phase 0-8 全部完成 | [[features/complaint-escalation]] · [[decisions/adr-007-complaint-escalation-stategraph]] |
| [2026-05-14](2026-05-14-customer-assistant-redesign/) | 商城客服助手流程重设计 | 设计完成；实现被 2026-05-18 intent-routing 覆盖 | [[decisions/adr-006-customer-assistant-separation]] |
| [2026-05-18](2026-05-18-agent-trace-eval/) | 自研 Agent Trace + 评估回放 | **已退役**（迁移 032 下线）；接续：LangFuse + Ragas | [[decisions/adr-015-langfuse-tracing]] · [[decisions/adr-014-ragas-integration]] |
| [2026-05-18](2026-05-18-intent-routing/) | 两层域路由意图识别 | Phase 0-3 实现，未上线（kill-switch 默认 false） | [[features/intent-routing]] · [[decisions/adr-008-intent-routing-two-tier]] |
| [2026-05-23](2026-05-23-markdown-visual-rag-l2/) | Markdown 图文 RAG L2 | Phase 5 完成（L3 保留） | [[features/markdown-visual-rag-l2]] · [[decisions/adr-011-markdown-visual-rag-l2]] |
| [2026-05-26](2026-05-26-markdown-structure-rag/) | Markdown 结构感知 RAG（small-to-big） | 已实现，端到端未联调 | [[features/markdown-structure-rag]] · [[decisions/adr-012-markdown-structure-rag]] |
| [2026-05-27](2026-05-27-markdown-image-rag/) | Markdown 图片 RAG（IMAGE_CAPTION） | 已实现（120 tests / 0 failures） | [[features/markdown-image-rag]] |
| [2026-05-28](2026-05-28-markdown-keyword-bm25/) | MD 关键词检索升级 BM25 | 代码落地、build 脚本未跑、默认仍 TRGM | [[features/md-keyword-bm25]] · [[decisions/adr-013-md-keyword-bm25]] |

## 长青路线图

| 文件 | 内容 |
|------|------|
| [roadmaps/plan.md](roadmaps/plan.md) | 可扩展内容与优化点规划（技术向：RAG / 性能 / 质量） |
| [roadmaps/plan2.md](roadmaps/plan2.md) | 可扩展内容与优化点规划（产品向：体验 / 安全 / 集成） |

## 命名约定

- 日期目录：`YYYY-MM-DD-<kebab-case-slug>/`
- 三件套（开发中）：`task_plan.md` / `progress.md` / `findings.md`，可附 `acceptance_report.md`
- 已结题工作流可简化为单 `plan.md`
- 每个日期目录 **必有** `README.md` 作为索引（标题 / 状态 / 文件清单 / 关联 wiki feature & ADR）
