package com.enterprise.kb.document.service;

import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;

import java.util.UUID;

/**
 * Markdown 结构感知入库服务。
 */
public interface MarkdownStructureIngestionService {

    /**
     * 按 H1-H3 parent 与段落级 child 解析 Markdown。
     *
     * @param documentId Markdown 文档 ID
     * @param spaceId    空间 ID
     * @param filePath   原始文件路径
     * @return parent、child 与向量文档的解析结果
     */
    MarkdownStructureIngestionResult parse(UUID documentId, UUID spaceId, String filePath);
}
