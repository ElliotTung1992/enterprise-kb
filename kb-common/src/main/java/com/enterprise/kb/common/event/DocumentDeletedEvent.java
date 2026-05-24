package com.enterprise.kb.common.event;

import java.util.UUID;

/**
 * 文档删除领域事件，由 DocumentService 发布，供跨模块级联清理监听。
 *
 * @param documentId 被删除的文档 ID
 */
public record DocumentDeletedEvent(UUID documentId) {}
