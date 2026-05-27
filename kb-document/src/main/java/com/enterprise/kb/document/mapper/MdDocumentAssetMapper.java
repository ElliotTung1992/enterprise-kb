package com.enterprise.kb.document.mapper;

import com.enterprise.kb.document.model.MdDocumentAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Markdown 图片资产 Mapper。
 */
@Mapper
public interface MdDocumentAssetMapper {

    /**
     * 批量插入 Markdown 图片资产。
     *
     * @param assets 资产列表
     */
    void insertBatch(@Param("list") List<MdDocumentAsset> assets);

    /**
     * 回填资产对应的 child chunk ID。
     *
     * @param id           资产 ID
     * @param childChunkId child chunk ID
     */
    void updateChildChunkId(@Param("id") UUID id, @Param("childChunkId") UUID childChunkId);

    /**
     * 根据 ID 查询资产。
     *
     * @param id 资产 ID
     * @return 资产
     */
    MdDocumentAsset findById(@Param("id") UUID id);

    /**
     * 删除文档下所有 Markdown 图片资产记录。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除空间下所有 Markdown 图片资产记录。
     *
     * @param spaceId 空间 ID
     */
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}

