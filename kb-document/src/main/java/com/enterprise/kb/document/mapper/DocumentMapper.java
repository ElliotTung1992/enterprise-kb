package com.enterprise.kb.document.mapper;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.model.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档数据访问 Mapper 接口
 */
@Mapper
public interface DocumentMapper {

    /**
     * 根据 ID 查询未删除文档
     *
     * @param id 文档 ID
     * @return 文档实体（Optional 包装）
     */
    Optional<Document> findByIdAndDeletedAtIsNull(@Param("id") UUID id);

    /**
     * 分页查询空间内文档（支持状态和关键词过滤）
     *
     * @param spaceId 空间 ID
     * @param status  文档状态（可为 null）
     * @param keyword 关键词（可为 null）
     * @return 文档列表（由 PageHelper 截断）
     */
    List<Document> findBySpaceId(@Param("spaceId") UUID spaceId,
                                  @Param("status") DocumentStatus status,
                                  @Param("keyword") String keyword);

    /**
     * 插入文档
     *
     * @param document 文档实体
     */
    void insert(Document document);

    /**
     * 更新文档
     *
     * @param document 文档实体
     */
    void update(Document document);

    /**
     * 查询空间内所有未删除文档的 ID
     *
     * @param spaceId 空间 ID
     * @return 文档 ID 列表
     */
    List<UUID> findIdsBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * 批量软删除空间内所有文档
     *
     * @param spaceId 空间 ID
     */
    void softDeleteBySpaceId(@Param("spaceId") UUID spaceId);
}
