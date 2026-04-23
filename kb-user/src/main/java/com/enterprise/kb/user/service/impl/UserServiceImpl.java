package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.auth.dto.RegisterRequest;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.user.service.UserService;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.user.dto.CreateUserRequest;
import com.enterprise.kb.user.dto.UserDto;
import com.enterprise.kb.user.mapper.UserMapper;
import com.enterprise.kb.user.model.User;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 用户服务实现，提供用户的创建、查询、列表与删除功能。
 * <p>支持软删除（设置 deleted_at），密码使用 BCrypt 加密存储。
 * 仅返回未软删除的有效用户。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 从注册流程创建用户（内部回调）。
     *
     * @param username     用户名
     * @param requestObj   注册请求对象
     * @return 新建用户 UUID
     */
    @Override
    @Transactional
    public UUID createFromRegister(String username, Object requestObj) {
        RegisterRequest req = (RegisterRequest) requestObj;
        if (userMapper.existsByUsernameAndDeletedAtIsNull(req.username()))
            throw new InvalidRequestException("Username already taken: " + req.username());
        if (userMapper.existsByEmailAndDeletedAtIsNull(req.email()))
            throw new InvalidRequestException("Email already registered: " + req.email());
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        userMapper.insert(user);
        return user.getId();
    }

    /**
     * 管理员创建用户。
     *
     * @param req 创建用户请求
     * @return 用户 DTO
     */
    @Override
    @Transactional
    public UserDto createUser(CreateUserRequest req) {
        if (userMapper.existsByUsernameAndDeletedAtIsNull(req.username()))
            throw new InvalidRequestException("Username already taken: " + req.username());
        if (userMapper.existsByEmailAndDeletedAtIsNull(req.email()))
            throw new InvalidRequestException("Email already registered: " + req.email());
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFullName(req.fullName());
        userMapper.insert(user);
        return toDto(user);
    }

    /**
     * 根据 UUID 获取用户信息。
     *
     * @param id 用户 UUID
     * @return 用户 DTO
     */
    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        return toDto(findActive(id));
    }

    /**
     * 根据用户名查询 UUID。
     *
     * @param username 用户名
     * @return 用户 UUID
     */
    @Override
    @Transactional(readOnly = true)
    public UUID getUserIdByUsername(String username) {
        return userMapper.findByUsernameAndDeletedAtIsNull(username)
                .map(User::getId).orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

    /**
     * 分页查询用户列表。
     *
     * @param keyword 关键字（匹配用户名、邮箱、全名）
     * @param page    页码（从 0 开始）
     * @param size    每页大小
     * @return 分页用户列表
     */
    @Override
    @Transactional(readOnly = true)
    public PageInfo<UserDto> listUsers(String keyword, int page, int size) {
        PageHelper.startPage(page + 1, size);
        List<User> users = userMapper.findAllActive(keyword);
        PageInfo<User> userPage = new PageInfo<>(users);
        PageInfo<UserDto> result = new PageInfo<>();
        result.setList(users.stream().map(this::toDto).toList());
        result.setTotal(userPage.getTotal());
        result.setPages(userPage.getPages());
        result.setPageNum(userPage.getPageNum());
        result.setPageSize(userPage.getPageSize());
        return result;
    }

    /**
     * 软删除用户。
     *
     * @param id 用户 UUID
     */
    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = findActive(id);
        user.setDeletedAt(Instant.now());
        user.setActive(false);
        user.setUpdatedAt(Instant.now());
        userMapper.update(user);
    }

    /**
     * 更新用户密码哈希（内部回调，由认证层在验证旧密码后调用）。
     *
     * @param id              用户 UUID
     * @param newPasswordHash 新密码的 BCrypt 哈希
     */
    @Override
    @Transactional
    public void updatePasswordHash(UUID id, String newPasswordHash) {
        User user = findActive(id);
        user.setPasswordHash(newPasswordHash);
        user.setUpdatedAt(Instant.now());
        userMapper.update(user);
    }

    private User findActive(UUID id) {
        User user = userMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
        if (user.getDeletedAt() != null) throw new ResourceNotFoundException("User", id);
        return user;
    }

    private UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.isActive(), u.getCreatedAt());
    }
}
