package com.enterprise.kb.document.mapper;

import com.enterprise.kb.common.constants.RelationType;
import com.enterprise.kb.document.model.DocumentRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 文档关系数据访问 Mapper 接口
 */
@Mapper
public interface DocumentRelationMapper {

    /**
     * 查询与指定文档相关的所有关系（作为源或目标）
     *
     * @param docId 文档 ID
     * @return 文档关系列表
     */
    List<DocumentRelation> findByDocumentId(@Param("docId") UUID docId);

    /**
     * 判断指定的文档关系是否已存在
     *
     * @param sourceDocId  源文档 ID
     * @param targetDocId  目标文档 ID
     * @param relationType 关系类型
     * @return true 表示已存在
     */
    boolean existsBySourceDocIdAndTargetDocIdAndRelationType(
            @Param("sourceDocId") UUID sourceDocId,
            @Param("targetDocId") UUID targetDocId,
            @Param("relationType") RelationType relationType);

    /**
     * 插入文档关系
     *
     * @param relation 关系实体
     */
    void insert(DocumentRelation relation);

    /**
     * 根据 ID 删除文档关系
     *
     * @param id 关系 ID
     */
    void deleteById(@Param("id") UUID id);
}
