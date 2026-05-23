package com.enterprise.kb.document.service;

import com.enterprise.kb.common.constants.DiagramType;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 流程图渲染服务。Phase 1 默认实现允许降级为空结果，后续可替换为 CLI 或渲染服务。
 */
public interface DiagramRenderService {

    /**
     * 将流程图源码渲染为图片。
     *
     * @param type       流程图类型
     * @param sourceCode 流程图源码
     * @param outputDir  输出目录
     * @return 渲染后的图片路径，渲染不可用或失败时返回空
     */
    Optional<Path> render(DiagramType type, String sourceCode, Path outputDir);
}
