package com.enterprise.kb.document.mapper;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.model.MdDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Markdown 文档 Mapper。
 */
@Mapper
public interface MdDocumentMapper {

    /**
     * 根据 ID 查询未删除 Markdown 文档。
     *
     * @param id 文档 ID
     * @return 文档实体
     */
    Optional<MdDocument> findByIdAndDeletedAtIsNull(@Param("id") UUID id);

    /**
     * 查询空间内 Markdown 文档。
     *
     * @param spaceId 空间 ID
     * @param status  状态过滤
     * @param keyword 标题关键词
     * @return 文档列表
     */
    List<MdDocument> findBySpaceId(@Param("spaceId") UUID spaceId,
                                   @Param("status") DocumentStatus status,
                                   @Param("keyword") String keyword);

    /**
     * 查询空间内未删除文档 ID。
     *
     * @param spaceId 空间 ID
     * @return 文档 ID 列表
     */
    List<UUID> findIdsBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * 插入 Markdown 文档。
     *
     * @param document 文档实体
     */
    void insert(MdDocument document);

    /**
     * 更新 Markdown 文档。
     *
     * @param document 文档实体
     */
    void update(MdDocument document);

    /**
     * 软删除空间内 Markdown 文档。
     *
     * @param spaceId 空间 ID
     */
    void softDeleteBySpaceId(@Param("spaceId") UUID spaceId);
}
