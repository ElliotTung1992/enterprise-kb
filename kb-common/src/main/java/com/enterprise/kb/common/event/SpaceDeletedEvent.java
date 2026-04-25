package com.enterprise.kb.common.event;

import java.util.UUID;

/**
 * 空间删除领域事件，由 SpaceService 发布，供跨模块级联清理监听。
 */
public record SpaceDeletedEvent(UUID spaceId) {}
