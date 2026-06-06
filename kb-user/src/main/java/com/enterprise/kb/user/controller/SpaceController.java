package com.enterprise.kb.user.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.user.dto.*;
import com.enterprise.kb.user.service.SpaceService;
import com.enterprise.kb.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 知识空间控制器
 * <p>提供知识空间的创建、查询、删除及成员管理（添加、变更角色、移除）等接口。
 * 空间是系统的多租户单元，每个空间有独立的文档、标签和权限管理。
 * 成员角色分为 VIEWER（只读）/ EDITOR（可编辑）/ ADMIN（管理员）三级。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final UserService userService;

    /**
     * 查询当前用户有权访问的空间列表
     * <p>返回当前登录用户在 user_space_roles 表中存在任意角色的所有空间。</p>
     *
     * @param userDetails 当前登录用户（Spring Security 注入）
     * @return 空间 DTO 列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SpaceDto>>> listSpaces(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(spaceService.listAccessibleSpaces(userId)));
    }

    /**
     * 创建新知识空间
     * <p>创建者自动被赋予该空间的 ADMIN 角色。</p>
     *
     * @param req         创建请求体，包含空间名称和描述
     * @param userDetails 当前登录用户（用于记录创建人并分配初始 ADMIN 角色）
     * @return HTTP 201 及新建空间的 DTO
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SpaceDto>> createSpace(
            @Valid @RequestBody CreateSpaceRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userService.getUserIdByUsername(userDetails.getUsername());
        return ResponseEntity.status(201)
                .body(ApiResponse.ok(spaceService.createSpace(req, userId)));
    }

    /**
     * 查询单个空间详情
     *
     * @param spaceId 空间 ID
     * @return 空间 DTO，包含名称、描述、成员数等基本信息
     */
    @GetMapping("/{spaceId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<SpaceDto>> getSpace(@PathVariable UUID spaceId) {
        return ResponseEntity.ok(ApiResponse.ok(spaceService.getSpace(spaceId)));
    }

    /**
     * 删除知识空间
     * <p>软删除空间及其所有关联数据（文档、标签、成员关系）。需要空间 ADMIN 权限。</p>
     *
     * @param spaceId 待删除的空间 ID
     * @return 删除成功提示
     */
    @DeleteMapping("/{spaceId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSpace(@PathVariable UUID spaceId) {
        spaceService.deleteSpace(spaceId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Space deleted"));
    }

    /**
     * 查询空间成员列表
     * <p>返回该空间内所有成员及其对应角色。</p>
     *
     * @param spaceId 空间 ID
     * @return 成员 DTO 列表，包含用户信息和角色类型
     */
    @GetMapping("/{spaceId}/members")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<SpaceMemberDto>>> listMembers(@PathVariable UUID spaceId) {
        return ResponseEntity.ok(ApiResponse.ok(spaceService.listMembers(spaceId)));
    }

    /**
     * 添加空间成员
     * <p>将指定用户以指定角色加入空间。需要空间 ADMIN 权限。</p>
     *
     * @param spaceId     空间 ID
     * @param req         成员请求体，包含目标用户 ID 和分配的角色
     * @param userDetails 当前登录用户（用于记录操作人）
     * @return 添加成功提示
     */
    @PostMapping("/{spaceId}/members")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @PathVariable UUID spaceId,
            @Valid @RequestBody SpaceMemberRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID grantedBy = userService.getUserIdByUsername(userDetails.getUsername());
        spaceService.addMember(spaceId, req.userId(), req.role(), grantedBy);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member added"));
    }

    /**
     * 变更成员角色
     * <p>更新指定成员在该空间内的角色。需要空间 ADMIN 权限。</p>
     *
     * @param spaceId     空间 ID
     * @param userId      目标成员的用户 ID
     * @param req         请求体，包含新角色
     * @param userDetails 当前登录用户（用于记录操作人）
     * @return 更新成功提示
     */
    @PutMapping("/{spaceId}/members/{userId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> changeMemberRole(
            @PathVariable UUID spaceId,
            @PathVariable UUID userId,
            @RequestBody SpaceMemberRequest req,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID grantedBy = userService.getUserIdByUsername(userDetails.getUsername());
        spaceService.changeMemberRole(spaceId, userId, req.role(), grantedBy);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role updated"));
    }

    /**
     * 移除空间成员
     * <p>将指定用户从空间中移除，删除其 user_space_roles 记录。需要空间 ADMIN 权限。</p>
     *
     * @param spaceId 空间 ID
     * @param userId  待移除的成员用户 ID
     * @return 移除成功提示
     */
    @DeleteMapping("/{spaceId}/members/{userId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID spaceId,
            @PathVariable UUID userId) {
        spaceService.removeMember(spaceId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Member removed"));
    }
}
