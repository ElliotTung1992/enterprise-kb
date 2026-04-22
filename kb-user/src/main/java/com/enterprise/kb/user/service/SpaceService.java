package com.enterprise.kb.user.service;

import com.enterprise.kb.common.constants.RoleType;
import com.enterprise.kb.user.dto.CreateSpaceRequest;
import com.enterprise.kb.user.dto.SpaceDto;
import com.enterprise.kb.user.dto.SpaceMemberDto;

import java.util.List;
import java.util.UUID;

/**
 * 知识空间服务，提供空间的创建、查询、成员管理与权限校验功能。
 */
public interface SpaceService {

    /**
     * 创建知识空间，并将创建者设为 ADMIN。
     *
     * @param req    创建请求
     * @param ownerId 所有者 UUID
     * @return 空间 DTO
     */
    SpaceDto createSpace(CreateSpaceRequest req, UUID ownerId);

    /**
     * 根据 UUID 获取空间信息。
     *
     * @param spaceId 空间 UUID
     * @return 空间 DTO
     */
    SpaceDto getSpace(UUID spaceId);

    /**
     * 获取用户可访问的所有空间列表。
     *
     * @param userId 用户 UUID
     * @return 空间列表
     */
    List<SpaceDto> listAccessibleSpaces(UUID userId);

    /**
     * 软删除空间。
     *
     * @param spaceId 空间 UUID
     */
    void deleteSpace(UUID spaceId);

    /**
     * 添加空间成员。
     *
     * @param spaceId   空间 UUID
     * @param userId    成员用户 UUID
     * @param role      角色
     * @param grantedBy 授权者 UUID
     */
    void addMember(UUID spaceId, UUID userId, RoleType role, UUID grantedBy);

    /**
     * 变更成员角色。
     *
     * @param spaceId   空间 UUID
     * @param userId    成员用户 UUID
     * @param newRole   新角色
     * @param grantedBy 授权者 UUID
     */
    void changeMemberRole(UUID spaceId, UUID userId, RoleType newRole, UUID grantedBy);

    /**
     * 移除空间成员。
     *
     * @param spaceId 空间 UUID
     * @param userId  成员用户 UUID
     */
    void removeMember(UUID spaceId, UUID userId);

    /**
     * 获取空间成员列表。
     *
     * @param spaceId 空间 UUID
     * @return 成员列表
     */
    List<SpaceMemberDto> listMembers(UUID spaceId);

    /**
     * 校验用户是否持有指定最低角色。
     *
     * @param userId        用户 UUID
     * @param spaceId        空间 UUID
     * @param minimumRole    最低角色
     * @return 是否满足角色要求
     */
    boolean hasRole(UUID userId, UUID spaceId, RoleType minimumRole);
}
