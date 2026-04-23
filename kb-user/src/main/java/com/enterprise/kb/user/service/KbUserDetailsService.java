package com.enterprise.kb.user.service;

import com.enterprise.kb.common.constants.RoleType;
import com.enterprise.kb.user.mapper.UserMapper;
import com.enterprise.kb.user.mapper.UserSpaceRoleMapper;
import com.enterprise.kb.user.model.User;
import com.enterprise.kb.user.model.UserSpaceRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security UserDetailsService 实现。
 * <p>根据用户名查询用户，并从 user_space_roles 表加载该用户在各空间的角色权限。
 * 系统管理员（spaceId 为 null 的 ADMIN）额外赋予 ROLE_SYSTEM_ADMIN。</p>
 */
@Service
@RequiredArgsConstructor
public class KbUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserSpaceRoleMapper userSpaceRoleMapper;

    /**
     * 根据用户名加载用户详情。
     * <p>从 user_space_roles 表加载用户在各个空间的角色，构建带空间限定的权限列表。
     * 系统级 ADMIN（spaceId 为 null）额外赋予 ROLE_SYSTEM_ADMIN。</p>
     *
     * @param username 用户名
     * @return Spring Security UserDetails
     * @throws UsernameNotFoundException 用户不存在
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<GrantedAuthority> authorities = new ArrayList<>();

        // Add space-scoped roles as authorities: ROLE_SPACE_{spaceId}_{ROLE}
        List<UserSpaceRole> spaceRoles = userSpaceRoleMapper.findByUserId(user.getId());
        for (UserSpaceRole role : spaceRoles) {
            authorities.add(new SimpleGrantedAuthority(
                    "ROLE_SPACE_" + role.getSpaceId() + "_" + role.getRoleType().name()));
        }

        // System ADMIN flag
        boolean isSystemAdmin = spaceRoles.stream()
                .anyMatch(r -> r.getRoleType() == RoleType.ADMIN && r.getSpaceId() == null);
        if (isSystemAdmin) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
        }

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isActive())
                .build();
    }
}
