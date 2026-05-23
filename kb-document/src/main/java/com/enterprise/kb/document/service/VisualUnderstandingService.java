package com.enterprise.kb.document.service;

import com.enterprise.kb.document.model.DocumentAsset;

/**
 * 视觉理解服务，封装 OCR、caption 和 summary provider。
 */
public interface VisualUnderstandingService {

    /**
     * 对视觉资产执行 OCR/caption/summary 处理。
     *
     * @param asset 待处理资产
     * @return 视觉理解结果
     */
    VisualUnderstandingResult understand(DocumentAsset asset);
}
