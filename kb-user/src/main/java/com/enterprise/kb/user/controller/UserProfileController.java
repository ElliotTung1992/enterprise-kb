package com.enterprise.kb.user.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.user.dto.UpdateProfileRequest;
import com.enterprise.kb.user.dto.UserProfileView;
import com.enterprise.kb.user.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 用户画像控制器（当前登录用户自身作用域）。
 *
 * <p>提供当前用户对自己画像的查看与显式声明修改。画像为全局粒度（一人一份，与 space 无关）。
 * 显式修改同步直写、即时生效；离线推断由后台异步写入，不覆盖显式值。设计见 ADR-016。</p>
 */
@RestController
@RequestMapping("/api/v1/users/me/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final ProfileService profileService;

    /**
     * 查询当前用户的画像服务态视图。
     * <p>返回合并显式与推断后的最终偏好，并标注资历来源（EXPLICIT/INFERRED）与置信度。</p>
     *
     * @return 当前用户画像视图
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileView>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile(SecurityUtils.getCurrentUserId())));
    }

    /**
     * 更新当前用户的画像显式声明。
     * <p>PUT 语义：整体替换显式声明各字段，字段传 null 表示清空（资历清空后回落到推断值）。
     * 同步写入、即时生效，不影响离线推断层。</p>
     *
     * @param req 画像更新请求（资历、答案长度/语言/风格、个性化总开关）
     * @return 更新后的画像视图
     */
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserProfileView>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        profileService.updateDeclared(userId, req);
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile(userId)));
    }
}
