package com.enterprise.kb.user.mapper;

import com.enterprise.kb.common.constants.RoleType;
import com.enterprise.kb.user.model.UserSpaceRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户空间角色数据访问 Mapper 接口
 */
@Mapper
public interface UserSpaceRoleMapper {

    /**
     * 查询空间内所有成员角色
     *
     * @param spaceId 空间 ID
     * @return 成员角色列表
     */
    List<UserSpaceRole> findBySpaceId(@Param("spaceId") UUID spaceId);

    /**
     * 查询用户所有角色
     *
     * @param userId 用户 ID
     * @return 角色列表
     */
    List<UserSpaceRole> findByUserId(@Param("userId") UUID userId);

    /**
     * 查询用户在指定空间的角色
     *
     * @param userId  用户 ID
     * @param spaceId 空间 ID
     * @return 角色实体（Optional 包装）
     */
    Optional<UserSpaceRole> findByUserIdAndSpaceId(@Param("userId") UUID userId,
                                                    @Param("spaceId") UUID spaceId);

    /**
     * 判断用户在指定空间是否拥有某角色
     *
     * @param userId   用户 ID
     * @param spaceId  空间 ID
     * @param roleType 角色类型
     * @return true 表示已存在
     */
    boolean existsByUserIdAndSpaceIdAndRoleType(@Param("userId") UUID userId,
                                                 @Param("spaceId") UUID spaceId,
                                                 @Param("roleType") RoleType roleType);

    /**
     * 判断用户在指定空间是否有任意角色
     *
     * @param userId  用户 ID
     * @param spaceId 空间 ID
     * @return true 表示已存在
     */
    boolean existsByUserIdAndSpaceId(@Param("userId") UUID userId, @Param("spaceId") UUID spaceId);

    /**
     * 插入用户空间角色
     *
     * @param userSpaceRole 角色实体
     */
    void insert(UserSpaceRole userSpaceRole);

    /**
     * 删除用户在指定空间的所有角色
     *
     * @param userId  用户 ID
     * @param spaceId 空间 ID
     */
    void deleteByUserIdAndSpaceId(@Param("userId") UUID userId, @Param("spaceId") UUID spaceId);
}
