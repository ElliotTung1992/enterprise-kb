package com.enterprise.kb.document.service;

import com.enterprise.kb.document.model.DocumentAsset;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Markdown 可视化解析结果。
 *
 * @param textDocuments      从 Markdown 正文解析出的文本 Document 列表
 * @param assets             从 Markdown 中抽取出的图片和流程图资产
 * @param sourceZipObjectKey 原始 zip 上传到 MinIO 后的 object key
 * @param mainMarkdownPath   zip 内主 Markdown 文件路径
 */
public record MarkdownVisualIngestionResult(
        List<Document> textDocuments,
        List<DocumentAsset> assets,
        String sourceZipObjectKey,
        String mainMarkdownPath
) {
    public static MarkdownVisualIngestionResult empty() {
        return new MarkdownVisualIngestionResult(List.of(), List.of(), null, null);
    }
}
