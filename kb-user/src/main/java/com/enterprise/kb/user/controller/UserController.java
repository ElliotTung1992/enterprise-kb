package com.enterprise.kb.user.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.user.dto.CreateUserRequest;
import com.enterprise.kb.user.dto.UserDto;
import com.enterprise.kb.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 用户管理控制器（系统管理员专用）
 * <p>提供用户账号的分页查询、单个查询、创建和删除接口，全部接口均需要 {@code ROLE_SYSTEM_ADMIN} 权限。
 * 普通用户的注册和密码修改请使用 {@code /api/v1/auth} 相关接口。</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 分页查询用户列表（系统管理员）
     * <p>支持按用户名关键词模糊搜索，返回未软删除的用户。</p>
     *
     * @param keyword 用户名关键词过滤（可选）
     * @param page    页码（从 0 开始，默认 0）
     * @param size    每页条数（默认 20）
     * @return 分页的用户 DTO 列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserDto>>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(
                PageResponse.of(userService.listUsers(keyword, page, size))));
    }

    /**
     * 查询单个用户详情（系统管理员）
     *
     * @param userId 目标用户 ID
     * @return 用户 DTO，包含用户名、邮箱、状态等信息
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUserById(userId)));
    }

    /**
     * 创建新用户（系统管理员）
     * <p>由管理员直接创建用户，不触发邮箱验证流程。创建的用户默认无系统管理员权限。</p>
     *
     * @param req 创建请求体，包含 username、email 和 password
     * @return HTTP 201 及新建用户的 DTO
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<UserDto>> createUser(@Valid @RequestBody CreateUserRequest req) {
        return ResponseEntity.status(201).body(ApiResponse.ok(userService.createUser(req)));
    }

    /**
     * 删除用户（系统管理员）
     * <p>软删除指定用户账号，设置 deleted_at 时间戳，同时撤销该用户的所有 refresh token。</p>
     *
     * @param userId 待删除的用户 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted"));
    }
}
