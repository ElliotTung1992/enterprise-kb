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
    private UUID userId;
    private UUID spaceId;
    private RoleType roleType;
    private Instant grantedAt = Instant.now();
    private UUID grantedBy;
}
