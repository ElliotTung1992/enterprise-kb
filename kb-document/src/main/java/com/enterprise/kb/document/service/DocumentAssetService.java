package com.enterprise.kb.document.service;

import com.enterprise.kb.document.dto.AssetCorrectionRequest;
import com.enterprise.kb.document.dto.AssetUrlResponse;
import com.enterprise.kb.document.dto.DocumentAssetDto;

import java.util.List;
import java.util.UUID;

/**
 * 文档视觉资产服务。
 */
public interface DocumentAssetService {

    /**
     * 查询文档下的视觉资产列表。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @return 视觉资产列表
     */
    List<DocumentAssetDto> listAssets(UUID spaceId, UUID documentId);

    /**
     * 查询视觉资产详情。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @param assetId    资产 ID
     * @return 视觉资产详情
     */
    DocumentAssetDto getAsset(UUID spaceId, UUID documentId, UUID assetId);

    /**
     * 生成资产内容短期访问 URL。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @param assetId    资产 ID
     * @return 短期访问 URL
     */
    AssetUrlResponse createContentUrl(UUID spaceId, UUID documentId, UUID assetId);

    /**
     * 保存人工修正并标记资产等待重建索引。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @param assetId    资产 ID
     * @param request    修正请求
     * @return 更新后的资产详情
     */
    DocumentAssetDto correctAsset(UUID spaceId, UUID documentId, UUID assetId, AssetCorrectionRequest request);
}
