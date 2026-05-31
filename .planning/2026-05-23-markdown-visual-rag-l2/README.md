# Markdown 图文 RAG L2（2026-05-23）

让 Markdown 文档中的正文、图片、流程图都能进入 RAG 检索链路：图片/流程图被转换成文本说明后进入文本向量检索（不引入图片向量）；原图与渲染图作为资产存储。

## 状态

实现至 Phase 5（asset worker / OCR / caption / 资产引用），后续保留 L3 多模态向量扩展。注意：当时 standard RAG 竖井仍在，故 `kb_chunks` 集合内有 `IMAGE_REFERENCE` / `DIAGRAM_SOURCE` / `IMAGE_CAPTION` / `DIAGRAM_SUMMARY` 类型；迁移 031 退役 standard RAG 后，相关功能聚焦在 [2026-05-26-markdown-structure-rag/](../2026-05-26-markdown-structure-rag/) 与 [2026-05-27-markdown-image-rag/](../2026-05-27-markdown-image-rag/) 的 md 竖井。

## 文件

- [task_plan.md](task_plan.md)
- [progress.md](progress.md)
- [findings.md](findings.md)

## 关联 wiki

- [[features/markdown-visual-rag-l2]]
- [[features/markdown-visual-rag-l2-design-notes]]
- [[decisions/adr-011-markdown-visual-rag-l2]]
