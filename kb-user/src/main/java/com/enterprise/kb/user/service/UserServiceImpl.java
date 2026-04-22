package com.enterprise.kb.user.service;

import com.enterprise.kb.auth.dto.RegisterRequest;
import com.enterprise.kb.common.exception.InvalidRequestException;
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

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

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

    @Override
    @Transactional(readOnly = true)
    public UserDto getUserById(UUID id) {
        return toDto(findActive(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UUID getUserIdByUsername(String username) {
        return userMapper.findByUsernameAndDeletedAtIsNull(username)
                .map(User::getId).orElseThrow(() -> new ResourceNotFoundException("User", username));
    }

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

    @Override
    @Transactional
    public void deleteUser(UUID id) {
        User user = findActive(id);
        user.setDeletedAt(Instant.now());
        user.setActive(false);
        user.setUpdatedAt(Instant.now());
        userMapper.update(user);
    }

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
