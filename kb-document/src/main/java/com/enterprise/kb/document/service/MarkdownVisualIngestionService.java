package com.enterprise.kb.document.service;

import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.pipeline.IngestionContext;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Markdown 可视化入库服务，负责处理 Markdown zip 中的图片和流程图资产。
 */
public interface MarkdownVisualIngestionService {

    /**
     * 判断当前摄取上下文是否是 Markdown zip 归档。
     *
     * @param ctx 摄取上下文
     * @return 是 Markdown zip 时返回 true
     */
    boolean supports(IngestionContext ctx);

    /**
     * 解析 Markdown zip，上传原始 zip 和视觉资产，返回文本文档及资产草稿。
     *
     * @param ctx 摄取上下文
     * @return 可视化解析结果
     */
    MarkdownVisualIngestionResult extract(IngestionContext ctx);

    /**
     * 保存视觉资产。
     *
     * @param assets 资产列表
     */
    void saveAssets(List<DocumentAsset> assets);

    /**
     * 删除文档下的旧视觉资产。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(java.util.UUID documentId);

    /**
     * 根据资产列表生成 Phase 1 视觉引用 chunk。
     *
     * @param ctx             摄取上下文
     * @param assets          资产列表
     * @param sectionAnchors  section 到最近文本 chunk 的映射
     * @return 视觉引用 chunk
     */
    List<Document> buildReferenceChunks(IngestionContext ctx, List<DocumentAsset> assets,
                                        java.util.Map<String, Integer> sectionAnchors);
}
