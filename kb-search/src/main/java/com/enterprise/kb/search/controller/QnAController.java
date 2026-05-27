package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.QaChatMessageDto;
import com.enterprise.kb.search.dto.QaChatSessionDto;
import com.enterprise.kb.search.dto.RenameSessionRequest;
import com.enterprise.kb.search.service.QaChatSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 问答会话管理控制器
 * <p>提供问答会话记录的增删改查（列表、历史消息、改名、删除）。会话数据由标准 RAG
 * 与 Markdown RAG 两条竖井共用同一张 {@code qa_sessions} 表，因此本控制器为两者共享。
 * 所有接口均通过 {@code @PreAuthorize} 进行空间级别的权限校验。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/qa")
@RequiredArgsConstructor
public class QnAController {

    private final QaChatSessionService sessionService;

    // ---- 会话管理接口 ----

    /**
     * 查询当前用户在指定空间下的会话列表
     * <p>按最后活跃时间倒序返回，每条记录包含标题、消息数、时间戳。</p>
     *
     * @param spaceId 空间 ID
     * @return 会话 DTO 列表
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<QaChatSessionDto>>> listSessions(
            @PathVariable UUID spaceId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sessionService.listSessions(spaceId, userId)));
    }

    /**
     * 查询指定会话的历史消息
     * <p>仅会话所有者可访问，消息按创建时间升序排列。</p>
     *
     * @param spaceId   空间 ID
     * @param sessionId 会话 ID
     * @return 消息 DTO 列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<List<QaChatMessageDto>>> getMessages(
            @PathVariable UUID spaceId,
            @PathVariable UUID sessionId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(sessionService.getMessages(sessionId, userId)));
    }

    /**
     * 修改会话标题
     *
     * @param spaceId   空间 ID
     * @param sessionId 会话 ID
     * @param req       包含新标题的请求体
     * @return 204 No Content
     */
    @PatchMapping("/sessions/{sessionId}/title")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<Void> renameSession(
            @PathVariable UUID spaceId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody RenameSessionRequest req) {
        UUID userId = SecurityUtils.getCurrentUserId();
        sessionService.renameSession(sessionId, userId, req.title());
        return ResponseEntity.noContent().build();
    }

    /**
     * 删除会话（软删除）并清空对应的 Redis 对话历史
     *
     * @param spaceId   空间 ID
     * @param sessionId 会话 ID
     * @return 204 No Content
     */
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID spaceId,
            @PathVariable UUID sessionId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        sessionService.deleteSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }
}
