package com.enterprise.kb.graph.mapper;

import com.enterprise.kb.graph.dto.TagDocumentDto;
import com.enterprise.kb.graph.model.DocumentTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 文档标签关联数据访问 Mapper 接口
 */
@Mapper
public interface DocumentTagMapper {

    /**
     * 查询文档的所有标签关联
     *
     * @param documentId 文档 ID
     * @return 文档标签关联列表
     */
    List<DocumentTag> findByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 查询标签关联的所有文档
     *
     * @param tagId 标签 ID
     * @return 文档标签关联列表
     */
    List<DocumentTag> findByTagId(@Param("tagId") UUID tagId);

    /**
     * 查询标签关联的文档详情列表（JOIN documents 表）
     *
     * @param tagId 标签 ID
     * @return 文档摘要 DTO 列表
     */
    List<TagDocumentDto> findDocumentsByTagId(@Param("tagId") UUID tagId);

    /**
     * 判断文档与标签的关联是否已存在
     *
     * @param documentId 文档 ID
     * @param tagId      标签 ID
     * @return true 表示已存在
     */
    boolean existsByDocumentIdAndTagId(@Param("documentId") UUID documentId,
                                        @Param("tagId") UUID tagId);

    /**
     * 插入文档标签关联
     *
     * @param documentTag 关联实体
     */
    void insert(DocumentTag documentTag);

    /**
     * 删除指定文档与标签的关联
     *
     * @param documentId 文档 ID
     * @param tagId      标签 ID
     */
    void deleteByDocumentIdAndTagId(@Param("documentId") UUID documentId,
                                     @Param("tagId") UUID tagId);

    /**
     * 将源标签的所有文档关联迁移到目标标签
     *
     * @param sourceTagId 源标签 ID
     * @param targetTagId 目标标签 ID
     */
    void mergeTags(@Param("sourceTagId") UUID sourceTagId, @Param("targetTagId") UUID targetTagId);

    /**
     * 删除文档的所有标签关联
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除标签的所有文档关联
     *
     * @param tagId 标签 ID
     */
    void deleteByTagId(@Param("tagId") UUID tagId);

    /**
     * 删除空间内所有文档和标签的关联
     *
     * @param spaceId 空间 ID
     */
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}
