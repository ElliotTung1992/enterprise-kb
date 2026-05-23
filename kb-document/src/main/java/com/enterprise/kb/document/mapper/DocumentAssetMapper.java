package com.enterprise.kb.document.mapper;

import com.enterprise.kb.document.model.DocumentAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
     * 查询待处理的视觉资产。
     *
     * @param limit 最大返回数量
     * @return 待处理资产列表
     */
    List<DocumentAsset> findPendingForProcessing(@Param("limit") int limit);

    /**
     * 根据 ID 查询资产。
     *
     * @param id 资产 ID
     * @return 资产实体
     */
    Optional<DocumentAsset> findById(@Param("id") UUID id);

    /**
     * 统计文档中指定状态的资产数量。
     *
     * @param documentId 文档 ID
     * @param statuses   状态名称列表
     * @return 资产数量
     */
    long countByDocumentIdAndStatuses(@Param("documentId") UUID documentId,
                                      @Param("statuses") List<String> statuses);

    /**
     * 将资产标记为处理中。
     *
     * @param id 资产 ID
     * @return 更新行数
     */
    int markProcessing(@Param("id") UUID id);

    /**
     * 保存视觉理解结果。
     *
     * @param asset 资产实体
     */
    void updateUnderstanding(@Param("asset") DocumentAsset asset);

    /**
     * 标记资产等待重试。
     *
     * @param id          资产 ID
     * @param nextRetryAt 下次重试时间
     * @param lastError   最近错误
     */
    void markRetry(@Param("id") UUID id,
                   @Param("nextRetryAt") Instant nextRetryAt,
                   @Param("lastError") String lastError);

    /**
     * 标记资产处理失败。
     *
     * @param id        资产 ID
     * @param lastError 最近错误
     */
    void markFailed(@Param("id") UUID id, @Param("lastError") String lastError);

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
