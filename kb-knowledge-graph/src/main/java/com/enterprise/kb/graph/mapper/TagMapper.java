package com.enterprise.kb.graph.mapper;

import com.enterprise.kb.graph.model.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 标签数据访问 Mapper 接口
 */
@Mapper
public interface TagMapper {

    /**
     * 根据 ID 查询未删除标签
     *
     * @param id 标签 ID
     * @return 标签实体（Optional 包装）
     */
    Optional<Tag> findByIdAndDeletedAtIsNull(@Param("id") UUID id);

    /**
     * 判断空间内 slug 是否已被未删除标签使用
     *
     * @param spaceId 空间 ID
     * @param slug    标签 slug
     * @return true 表示已存在
     */
    boolean existsBySpaceIdAndSlugAndDeletedAtIsNull(@Param("spaceId") UUID spaceId,
                                                      @Param("slug") String slug);

    /**
     * 查询空间内所有标签（排序：sort_order 升序、name 升序）
     *
     * @param spaceId 空间 ID
     * @return 标签列表
     */
    List<Tag> findBySpaceIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(@Param("spaceId") UUID spaceId);

    /**
     * 使用递归 CTE 查询空间标签树（原始行数据）
     *
     * @param spaceId 空间 ID
     * @return 带深度字段的标签列表（已按 depth、sort_order、name 排序）
     */
    List<Tag> findTagTree(@Param("spaceId") UUID spaceId);

    /**
     * 查询所有标签
     *
     * @return 所有标签列表
     */
    List<Tag> findAll();

    /**
     * 插入标签
     *
     * @param tag 标签实体
     */
    void insert(Tag tag);

    /**
     * 更新标签
     *
     * @param tag 标签实体
     */
    void update(Tag tag);

    /**
     * 批量软删除空间内所有标签
     *
     * @param spaceId 空间 ID
     */
    void softDeleteBySpaceId(@Param("spaceId") UUID spaceId);
}
