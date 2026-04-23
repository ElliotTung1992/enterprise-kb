package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.common.constants.RoleType;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.user.service.SpaceService;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.common.util.SlugUtils;
import com.enterprise.kb.user.dto.CreateSpaceRequest;
import com.enterprise.kb.user.dto.SpaceDto;
import com.enterprise.kb.user.dto.SpaceMemberDto;
import com.enterprise.kb.user.mapper.SpaceMapper;
import com.enterprise.kb.user.mapper.UserMapper;
import com.enterprise.kb.user.mapper.UserSpaceRoleMapper;
import com.enterprise.kb.user.model.Space;
import com.enterprise.kb.user.model.User;
import com.enterprise.kb.user.model.UserSpaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 知识空间服务实现，提供空间的创建、查询、成员管理与权限校验功能。
 * <p>空间创建者自动成为 ADMIN；成员角色分为 VIEWER / EDITOR / ADMIN 三级。
 * 软删除空间会同时保留关联数据，仅标记 deleted_at。</p>
 */
@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final SpaceMapper spaceMapper;
    private final UserMapper userMapper;
    private final UserSpaceRoleMapper userSpaceRoleMapper;

    /**
     * 创建知识空间，并将创建者设为 ADMIN。
     *
     * @param req    创建请求
     * @param ownerId 所有者 UUID
     * @return 空间 DTO
     */
    @Override
    @Transactional
    public SpaceDto createSpace(CreateSpaceRequest req, UUID ownerId) {
        String slug = req.slug() != null ? req.slug() : SlugUtils.toSlug(req.name());
        if (spaceMapper.existsBySlugAndDeletedAtIsNull(slug))
            throw new InvalidRequestException("Space slug already exists: " + slug);
        Space space = new Space();
        space.setId(UUID.randomUUID());
        space.setName(req.name());
        space.setSlug(slug);
        space.setDescription(req.description());
        space.setOwnerId(ownerId);
        space.setPreferredModelProvider(req.preferredModelProvider());
        spaceMapper.insert(space);
        addMember(space.getId(), ownerId, RoleType.ADMIN, ownerId);
        return toDto(space);
    }

    /**
     * 根据 UUID 获取空间信息。
     *
     * @param spaceId 空间 UUID
     * @return 空间 DTO
     */
    @Override
    @Transactional(readOnly = true)
    public SpaceDto getSpace(UUID spaceId) {
        return toDto(findActive(spaceId));
    }

    /**
     * 获取用户可访问的所有空间列表。
     *
     * @param userId 用户 UUID
     * @return 空间列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<SpaceDto> listAccessibleSpaces(UUID userId) {
        return spaceMapper.findAccessibleByUserId(userId).stream().map(this::toDto).toList();
    }

    /**
     * 软删除空间。
     *
     * @param spaceId 空间 UUID
     */
    @Override
    @Transactional
    public void deleteSpace(UUID spaceId) {
        Space space = findActive(spaceId);
        space.setDeletedAt(Instant.now());
        space.setActive(false);
        space.setUpdatedAt(Instant.now());
        spaceMapper.update(space);
    }

    /**
     * 添加空间成员。
     *
     * @param spaceId   空间 UUID
     * @param userId    成员用户 UUID
     * @param role      角色
     * @param grantedBy 授权者 UUID
     */
    @Override
    @Transactional
    public void addMember(UUID spaceId, UUID userId, RoleType role, UUID grantedBy) {
        if (userSpaceRoleMapper.existsByUserIdAndSpaceIdAndRoleType(userId, spaceId, role)) return;
        UserSpaceRole usr = new UserSpaceRole();
        usr.setId(UUID.randomUUID());
        usr.setUserId(userId);
        usr.setSpaceId(spaceId);
        usr.setRoleType(role);
        usr.setGrantedBy(grantedBy);
        userSpaceRoleMapper.insert(usr);
    }

    /**
     * 变更成员角色。
     *
     * @param spaceId   空间 UUID
     * @param userId    成员用户 UUID
     * @param newRole   新角色
     * @param grantedBy 授权者 UUID
     */
    @Override
    @Transactional
    public void changeMemberRole(UUID spaceId, UUID userId, RoleType newRole, UUID grantedBy) {
        userSpaceRoleMapper.deleteByUserIdAndSpaceId(userId, spaceId);
        addMember(spaceId, userId, newRole, grantedBy);
    }

    /**
     * 移除空间成员。
     *
     * @param spaceId 空间 UUID
     * @param userId  成员用户 UUID
     */
    @Override
    @Transactional
    public void removeMember(UUID spaceId, UUID userId) {
        userSpaceRoleMapper.deleteByUserIdAndSpaceId(userId, spaceId);
    }

    /**
     * 获取空间成员列表。
     *
     * @param spaceId 空间 UUID
     * @return 成员列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<SpaceMemberDto> listMembers(UUID spaceId) {
        return userSpaceRoleMapper.findBySpaceId(spaceId).stream().map(usr -> {
            User user = userMapper.findById(usr.getUserId()).orElse(null);
            return new SpaceMemberDto(usr.getUserId(),
                    user != null ? user.getUsername() : "unknown",
                    user != null ? user.getEmail() : null,
                    usr.getRoleType(), usr.getGrantedAt());
        }).toList();
    }

    /**
     * 校验用户是否持有指定最低角色。
     *
     * @param userId        用户 UUID
     * @param spaceId        空间 UUID
     * @param minimumRole    最低角色
     * @return 是否满足角色要求
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasRole(UUID userId, UUID spaceId, RoleType minimumRole) {
        return userSpaceRoleMapper.findByUserIdAndSpaceId(userId, spaceId)
                .map(usr -> usr.getRoleType().ordinal() <= minimumRole.ordinal()).orElse(false);
    }

    private Space findActive(UUID id) {
        return spaceMapper.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> new ResourceNotFoundException("Space", id));
    }

    private SpaceDto toDto(Space s) {
        return new SpaceDto(s.getId(), s.getName(), s.getSlug(), s.getDescription(),
                s.getOwnerId(), s.getPreferredModelProvider(), s.isActive(), s.getCreatedAt());
    }
}
