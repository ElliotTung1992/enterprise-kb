package com.enterprise.kb.document.markdown;

import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdParentChunk;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Markdown 结构化解析结果。
 *
 * @param parents         parent chunk 列表
 * @param children        child chunk 列表
 * @param vectorDocuments 待写入向量库的 child 文档
 */
public record MarkdownStructureIngestionResult(
        List<MdParentChunk> parents,
        List<MdChildChunk> children,
        List<Document> vectorDocuments
) {}
