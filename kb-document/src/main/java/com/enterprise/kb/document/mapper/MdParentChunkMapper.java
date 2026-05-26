package com.enterprise.kb.document.mapper;

import com.enterprise.kb.document.model.MdParentChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Markdown parent chunk Mapper。
 */
@Mapper
public interface MdParentChunkMapper {

    /**
     * 批量插入 parent chunk。
     *
     * @param chunks parent 列表
     */
    void insertBatch(@Param("list") List<MdParentChunk> chunks);

    /**
     * 根据 ID 查询 parent。
     *
     * @param id parent ID
     * @return parent chunk
     */
    MdParentChunk findById(@Param("id") UUID id);

    /**
     * 批量查询 parent。
     *
     * @param ids parent ID 列表
     * @return parent 列表
     */
    List<MdParentChunk> findByIds(@Param("ids") List<UUID> ids);

    /**
     * 删除文档下所有 parent。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除空间下所有 parent。
     *
     * @param spaceId 空间 ID
     */
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}
