package com.enterprise.kb.user.service;

import com.enterprise.kb.user.dto.CreateUserRequest;
import com.enterprise.kb.user.dto.UserDto;
import com.github.pagehelper.PageInfo;

import java.util.UUID;

/**
 * 用户服务，提供用户的创建、查询、列表与删除功能。
 */
public interface UserService {

    /**
     * 从注册流程创建用户（内部回调）。
     *
     * @param username     用户名
     * @param requestObj   注册请求对象
     * @return 新建用户 UUID
     */
    UUID createFromRegister(String username, Object requestObj);

    /**
     * 管理员创建用户。
     *
     * @param req 创建用户请求
     * @return 用户 DTO
     */
    UserDto createUser(CreateUserRequest req);

    /**
     * 根据 UUID 获取用户信息。
     *
     * @param id 用户 UUID
     * @return 用户 DTO
     */
    UserDto getUserById(UUID id);

    /**
     * 根据用户名查询 UUID。
     *
     * @param username 用户名
     * @return 用户 UUID
     */
    UUID getUserIdByUsername(String username);

    /**
     * 分页查询用户列表。
     *
     * @param keyword 关键字（匹配用户名、邮箱、全名）
     * @param page    页码（从 0 开始）
     * @param size    每页大小
     * @return 分页用户列表
     */
    PageInfo<UserDto> listUsers(String keyword, int page, int size);

    /**
     * 软删除用户。
     *
     * @param id 用户 UUID
     */
    void deleteUser(UUID id);

    /**
     * 更新用户密码哈希（内部回调，由认证层在验证旧密码后调用）。
     *
     * @param id              用户 UUID
     * @param newPasswordHash 新密码的 BCrypt 哈希
     */
    void updatePasswordHash(UUID id, String newPasswordHash);
}
