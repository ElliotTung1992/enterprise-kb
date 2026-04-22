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

@Service
@RequiredArgsConstructor
public class SpaceServiceImpl implements SpaceService {

    private final SpaceMapper spaceMapper;
    private final UserMapper userMapper;
    private final UserSpaceRoleMapper userSpaceRoleMapper;

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

    @Override
    @Transactional(readOnly = true)
    public SpaceDto getSpace(UUID spaceId) {
        return toDto(findActive(spaceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceDto> listAccessibleSpaces(UUID userId) {
        return spaceMapper.findAccessibleByUserId(userId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public void deleteSpace(UUID spaceId) {
        Space space = findActive(spaceId);
        space.setDeletedAt(Instant.now());
        space.setActive(false);
        space.setUpdatedAt(Instant.now());
        spaceMapper.update(space);
    }

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

    @Override
    @Transactional
    public void changeMemberRole(UUID spaceId, UUID userId, RoleType newRole, UUID grantedBy) {
        userSpaceRoleMapper.deleteByUserIdAndSpaceId(userId, spaceId);
        addMember(spaceId, userId, newRole, grantedBy);
    }

    @Override
    @Transactional
    public void removeMember(UUID spaceId, UUID userId) {
        userSpaceRoleMapper.deleteByUserIdAndSpaceId(userId, spaceId);
    }

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
