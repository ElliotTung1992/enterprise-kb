package com.enterprise.kb.user.mapper;

import com.enterprise.kb.user.model.Space;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识空间数据访问 Mapper 接口
 */
@Mapper
public interface SpaceMapper {

    /**
     * 根据 slug 查询未删除空间
     *
     * @param slug 空间 slug
     * @return 空间实体（Optional 包装）
     */
    Optional<Space> findBySlugAndDeletedAtIsNull(@Param("slug") String slug);

    /**
     * 判断 slug 是否已被未删除空间使用
     *
     * @param slug 空间 slug
     * @return true 表示已存在
     */
    boolean existsBySlugAndDeletedAtIsNull(@Param("slug") String slug);

    /**
     * 根据 ID 查询未删除空间
     *
     * @param id 空间 ID
     * @return 空间实体（Optional 包装）
     */
    Optional<Space> findByIdAndDeletedAtIsNull(@Param("id") UUID id);

    /**
     * 查询用户有权访问的所有空间
     *
     * @param userId 用户 ID
     * @return 空间列表
     */
    List<Space> findAccessibleByUserId(@Param("userId") UUID userId);

    /**
     * 插入空间
     *
     * @param space 空间实体
     */
    void insert(Space space);

    /**
     * 更新空间
     *
     * @param space 空间实体
     */
    void update(Space space);
}
