package com.enterprise.kb.auth.controller;

import com.enterprise.kb.auth.dto.*;
import com.enterprise.kb.auth.service.AuthService;
import com.enterprise.kb.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * <p>提供用户登录、注册、Token 刷新、登出及当前用户信息查询等接口。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 用户登录
     * <p>校验用户名和密码，成功后返回 access token 与 refresh token。</p>
     *
     * @param request 登录请求体，包含 username 和 password
     * @return 包含 JWT token 信息的响应
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    /**
     * 用户注册
     * <p>创建新用户账号，成功后自动颁发 token，无需再次登录。</p>
     *
     * @param request 注册请求体，包含 username、email 和 password
     * @return HTTP 201 及包含 JWT token 信息的响应
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(201).body(ApiResponse.ok(response, "Registration successful"));
    }

    /**
     * 刷新 Access Token
     * <p>使用有效的 refresh token 换取新的 access token，旧 refresh token 同时失效（旋转策略）。</p>
     *
     * @param request 包含 refreshToken 的请求体
     * @return 新的 JWT token 信息
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.refreshToken())));
    }

    /**
     * 用户登出
     * <p>撤销指定的 refresh token，使其立即失效。</p>
     *
     * @param request 包含待撤销 refreshToken 的请求体
     * @return 登出成功提示
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }

    /**
     * 获取当前登录用户信息
     * <p>从 SecurityContext 中读取已认证的用户详情，无需额外参数。</p>
     *
     * @param userDetails Spring Security 注入的当前用户详情
     * @return 当前用户的 UserDetails
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDetails>> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(userDetails));
    }

    /**
     * 修改当前用户密码
     *
     * @param userDetails 当前登录用户
     * @param request     包含旧密码和新密码的请求体
     * @return 修改成功提示
     */
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getUsername(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed"));
    }
}
