package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.document.dto.AssetCorrectionRequest;
import com.enterprise.kb.document.dto.AssetUrlResponse;
import com.enterprise.kb.document.dto.DocumentAssetDto;
import com.enterprise.kb.document.mapper.DocumentAssetMapper;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.service.DocumentAssetService;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 文档视觉资产服务实现。
 */
@Service
@RequiredArgsConstructor
public class DocumentAssetServiceImpl implements DocumentAssetService {

    private final DocumentMapper documentMapper;
    private final DocumentAssetMapper assetMapper;
    private final DocumentObjectStorageService objectStorageService;

    @Value("${enterprise.kb.document.asset-url-expiry-seconds:300}")
    private int assetUrlExpirySeconds;

    @Override
    @Transactional(readOnly = true)
    public List<DocumentAssetDto> listAssets(UUID spaceId, UUID documentId) {
        assertDocumentInSpace(spaceId, documentId);
        return assetMapper.findByDocumentId(documentId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAssetDto getAsset(UUID spaceId, UUID documentId, UUID assetId) {
        assertDocumentInSpace(spaceId, documentId);
        return toDto(findAsset(documentId, assetId));
    }

    @Override
    @Transactional(readOnly = true)
    public AssetUrlResponse createContentUrl(UUID spaceId, UUID documentId, UUID assetId) {
        assertDocumentInSpace(spaceId, documentId);
        DocumentAsset asset = findAsset(documentId, assetId);
        if (!StringUtils.hasText(asset.getObjectKey())) {
            throw new InvalidRequestException("该资产没有可访问的对象内容");
        }
        return new AssetUrlResponse(
                objectStorageService.presignedGetUrl(asset.getObjectKey(), assetUrlExpirySeconds),
                assetUrlExpirySeconds);
    }

    @Override
    @Transactional
    public DocumentAssetDto correctAsset(UUID spaceId, UUID documentId, UUID assetId, AssetCorrectionRequest request) {
        assertDocumentInSpace(spaceId, documentId);
        findAsset(documentId, assetId);
        assetMapper.updateManualCorrection(assetId, request.manualCaption(), request.manualSummary());
        return toDto(findAsset(documentId, assetId));
    }

    private void assertDocumentInSpace(UUID spaceId, UUID documentId) {
        Document document = documentMapper.findByIdAndDeletedAtIsNull(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
        if (!document.getSpaceId().equals(spaceId)) {
            throw new ResourceNotFoundException("Document", documentId);
        }
    }

    private DocumentAsset findAsset(UUID documentId, UUID assetId) {
        return assetMapper.findByDocumentIdAndAssetId(documentId, assetId)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentAsset", assetId));
    }

    private DocumentAssetDto toDto(DocumentAsset asset) {
        return new DocumentAssetDto(
                asset.getId(), asset.getDocumentId(), asset.getAssetType(), asset.getDiagramType(),
                asset.getAssetIndex(), asset.getOriginalPath(), asset.getObjectKey(), asset.getMimeType(),
                asset.getFileSize(), asset.getSection(), asset.getAnchorChunkIndex(), asset.getAltText(),
                asset.getTitle(), asset.getSourceCode(), asset.getOcrText(), asset.getCaption(),
                asset.getSummary(), asset.getEntities(), asset.getManualCaption(), asset.getManualSummary(),
                asset.getStatus(), asset.getLastError(), asset.getCreatedAt(), asset.getUpdatedAt());
    }
}
