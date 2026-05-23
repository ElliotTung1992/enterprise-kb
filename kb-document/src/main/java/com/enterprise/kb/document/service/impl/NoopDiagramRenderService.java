package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.DiagramType;
import com.enterprise.kb.document.service.DiagramRenderService;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 默认流程图渲染实现：不执行渲染，仅让入库流程降级使用源码 chunk。
 */
@Service
public class NoopDiagramRenderService implements DiagramRenderService {

    @Override
    public Optional<Path> render(DiagramType type, String sourceCode, Path outputDir) {
        return Optional.empty();
    }
}
