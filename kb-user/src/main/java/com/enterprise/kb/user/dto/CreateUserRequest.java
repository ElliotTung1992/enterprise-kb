package com.enterprise.kb.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建用户请求。
 *
 * @param username 用户名
 * @param email    邮箱
 * @param password 密码明文
 * @param fullName 用户姓名
 */
public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        String fullName
) {}
