package com.enterprise.kb.user.security;

import com.enterprise.kb.common.constants.RoleType;
import com.enterprise.kb.user.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

/**
 * Enables @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
 */
@Component
@RequiredArgsConstructor
public class SpacePermissionEvaluator implements PermissionEvaluator {

    private final SpaceService spaceService;
    private final com.enterprise.kb.user.service.UserService userService;

    @Override
    public boolean hasPermission(Authentication auth, Object targetDomainObject, Object permission) {
        return false;
    }

    @Override
    public boolean hasPermission(Authentication auth, Serializable targetId,
                                 String targetType, Object permission) {
        if (auth == null || !auth.isAuthenticated()) return false;
        if (!"SPACE".equals(targetType)) return false;

        String username = ((UserDetails) auth.getPrincipal()).getUsername();
        UUID userId = userService.getUserIdByUsername(username);
        UUID spaceId = targetId instanceof UUID uid ? uid : UUID.fromString(targetId.toString());
        RoleType required = RoleType.valueOf(permission.toString());

        return spaceService.hasRole(userId, spaceId, required);
    }
}
