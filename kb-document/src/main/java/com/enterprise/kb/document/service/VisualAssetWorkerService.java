package com.enterprise.kb.document.service;

import com.enterprise.kb.document.model.DocumentAsset;

/**
 * 视觉资产异步处理服务。
 */
public interface VisualAssetWorkerService {

    /**
     * 扫描并处理一批待处理视觉资产。
     */
    void processPendingAssets();

    /**
     * 处理单个视觉资产。
     *
     * @param asset 视觉资产
     */
    void processAsset(DocumentAsset asset);
}
