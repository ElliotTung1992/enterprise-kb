# Wiki Ingest Log

## 2026-05-26 | ingest | Markdown 结构感知 RAG 验收文档
- Source: `docs/acceptance-md-structure-rag.md`
- Summary: [[features/markdown-structure-rag-acceptance]]
- Pages created:
  - [[features/markdown-structure-rag]]
  - [[features/markdown-structure-rag-acceptance]]
  - [[decisions/adr-012-markdown-structure-rag]]（ingest 后补建：提炼 C1–C17 核心架构决策）
- Pages updated:
  - [[Home]] — 功能导航 + 架构决策导航
  - [[database/migrations]] — 新增迁移 027/028
- Key insight: 「结构感知 RAG（small-to-big 父子索引）」是与「图文 RAG L2」**不同的另一条平行竖井**（`md_documents`/`md_kb_chunks`，标准竖井零改动）；child 边界只决定召回命中，回答返回完整 parent，故弃用标准链 100-token overlap；融合阶段不去重以保留多 child 位置供多窗节选。

## 2026-05-18 | save | 客服助手意图识别 — 两层域路由
- Type: feature
- Location: wiki/features/intent-routing.md
- From: grill-me 设计评审 → ADR-008 → 分阶段实施计划 → Phase 0–3 全部实现
- Pages created:
  - [[features/intent-routing]]
- Pages updated:
  - [[Home]] — 功能导航新增意图路由
- Key insight: 意图识别准确率拆成"域路由 vs 域内工具"两层独立度量；few-shot 与冻结测试集必须硬切分防污染；`routing-enabled` kill-switch 默认 false，先影子后灰度。

## 2026-05-13 | ingest | HITL 售后审核 & 商城客服助手
- Source: `.planning/2026-05-13-hitl-after-sales/` (task_plan.md + findings.md + progress.md)
- Summary: [[features/hitl-after-sales]]
- Pages created:
  - [[features/hitl-after-sales]]
  - [[ai-rag/hitl-hook]]
  - [[api/after-sales]]
  - [[database/entities/after-sales-tables]]
  - [[decisions/adr-005-hitl-transaction-ordering]]
  - [[decisions/adr-006-customer-assistant-separation]]
- Pages updated:
  - [[Home]] — 新增功能、AI/RAG、API、架构决策导航
  - [[database/schema-overview]] — 新增迁移 018-020 和售后表实体链接
  - [[ai-rag/agentic-qa]] — 标注 HITL 已从此服务移除
- Key insight: HITL 审批必须先调用 LLM（resumeWithFeedback）再提交 DB，否则 LLM 失败后申请状态已变无法重试。
