package com.enterprise.kb.document.mapper;

import com.enterprise.kb.document.model.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 文档分块数据访问 Mapper 接口
 */
@Mapper
public interface DocumentChunkMapper {

    /**
     * 按块序号查询文档的所有分块
     *
     * @param documentId 文档 ID
     * @return 分块列表（按 chunk_index 升序）
     */
    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(@Param("documentId") UUID documentId);

    /**
     * 插入单个分块
     *
     * @param chunk 分块实体
     */
    void insert(DocumentChunk chunk);

    /**
     * 批量插入分块
     *
     * @param chunks 分块列表
     */
    void insertBatch(@Param("chunks") List<DocumentChunk> chunks);

    /**
     * 统计文档的分块数
     *
     * @param documentId 文档 ID
     * @return 分块数量
     */
    long countByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除文档的所有分块
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除空间内所有文档的分块
     *
     * @param spaceId 空间 ID
     */
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}
