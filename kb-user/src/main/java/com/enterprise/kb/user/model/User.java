package com.enterprise.kb.user.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class User {

    private UUID id;
    /** 用户名（登录名，唯一） */
    private String username;
    /** 邮箱（唯一） */
    private String email;
    /** BCrypt 加密后的密码 */
    private String passwordHash;
    /** 真实姓名 */
    private String fullName;
    /** 账户是否启用 */
    private boolean active = true;
    /** 创建时间 */
    private Instant createdAt = Instant.now();
    /** 最后更新时间 */
    private Instant updatedAt = Instant.now();
    /** 软删除时间，为 null 表示未删除 */
    private Instant deletedAt;
}
