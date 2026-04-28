package com.enterprise.kb.user.model;

import com.enterprise.kb.common.constants.RoleType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class UserSpaceRole {

    private UUID id;
    /** 用户 ID */
    private UUID userId;
    /** 知识空间 ID */
    private UUID spaceId;
    /** 角色类型（VIEWER / EDITOR / ADMIN） */
    private RoleType roleType;
    /** 授权时间 */
    private Instant grantedAt = Instant.now();
    /** 授权人用户 ID */
    private UUID grantedBy;
}
