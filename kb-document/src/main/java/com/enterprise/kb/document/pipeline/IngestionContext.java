package com.enterprise.kb.document.pipeline;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/**
 * 文档入库上下文。
 * <p>用于在异步入库流水线中传递文档、空间、文件路径和模型选择等必要信息。</p>
 */
@Getter
@Builder
public class IngestionContext {
    /** 待入库文档 ID */
    private final UUID documentId;
    /** 文档所属知识空间 ID */
    private final UUID spaceId;
    /** 本地文件路径 */
    private final String filePath;
    /** 文件 MIME 类型 */
    private final String mimeType;
    /** 指定的 embedding provider，为空时使用系统默认配置 */
    private final String embeddingProvider;
}
