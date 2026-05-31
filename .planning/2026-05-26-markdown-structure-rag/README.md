# Markdown 结构感知 RAG（2026-05-26）

small-to-big 父子索引重构：按 H1-H3 切 parent（整段 section），parent 内再切段落级 child；检索走 child 粒度（RRF 融合不去重），回答返回完整 parent。与 [2026-05-23-markdown-visual-rag-l2/](../2026-05-23-markdown-visual-rag-l2/) 是**两条不同竖井**，走全新 `md_documents` / `md_kb_chunks`，标准竖井零改动。

## 状态

已实现。入库单测 7/7 通过；检索 / Agentic 无自动化测试、端到端未联调。

## 文件

- [plan.md](plan.md) — Phase 0 Allowed APIs 核实 + 分阶段实施计划 + 反模式守卫

## 关联 wiki

- [[features/markdown-structure-rag]]
- [[features/markdown-structure-rag-acceptance]]
- [[decisions/adr-012-markdown-structure-rag]]
- 设计文档：`docs/design-md-structure-rag.md` / `docs/acceptance-md-structure-rag.md`
