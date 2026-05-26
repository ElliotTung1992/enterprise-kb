package com.enterprise.kb.document.mapper;

import com.enterprise.kb.document.model.MdChildChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Markdown child chunk Mapper。
 */
@Mapper
public interface MdChildChunkMapper {

    /**
     * 批量插入 child chunk。
     *
     * @param chunks child 列表
     */
    void insertBatch(@Param("list") List<MdChildChunk> chunks);

    /**
     * 根据 ID 查询 child。
     *
     * @param id child ID
     * @return child chunk
     */
    MdChildChunk findById(@Param("id") UUID id);

    /**
     * 查询 parent 下所有 child，按顺序返回。
     *
     * @param parentId parent ID
     * @return child 列表
     */
    List<MdChildChunk> findByParentId(@Param("parentId") UUID parentId);

    /**
     * 删除文档下所有 child。
     *
     * @param documentId 文档 ID
     */
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /**
     * 删除空间下所有 child。
     *
     * @param spaceId 空间 ID
     */
    void deleteBySpaceId(@Param("spaceId") UUID spaceId);
}
