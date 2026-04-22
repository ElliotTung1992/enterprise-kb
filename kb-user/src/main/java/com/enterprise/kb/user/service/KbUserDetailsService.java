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

@Service
@RequiredArgsConstructor
public class KbUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;
    private final UserSpaceRoleMapper userSpaceRoleMapper;

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
