package com.enterprise.kb.document.mapper;

import com.enterprise.kb.document.model.DocumentAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 文档视觉资产数据访问 Mapper 接口。
 */
@Mapper
public interface DocumentAssetMapper {

    /**
     * 批量插入文档视觉资产。
     *
     * @param assets 资产列表
     */
    void insertBatch(@Param("assets") List<DocumentAsset> assets);

    /**
     * 查询文档下的所有视觉资产。
     *
     * @param documentId 文档 ID
     * @return 资产列表
     */
    List<DocumentAsset> findByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除文档下的所有视觉资产。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除空间内所有文档的视觉资产。
     *
     * @param spaceId 空间 ID
     */
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}
