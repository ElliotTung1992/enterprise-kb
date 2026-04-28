package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.QaChatMessageDto;
import com.enterprise.kb.search.dto.QaChatSessionDto;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.dto.RenameSessionRequest;
import com.enterprise.kb.search.service.AgenticQnAService;
import com.enterprise.kb.search.service.QaChatSessionService;
import com.enterprise.kb.search.service.QnAService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * RAG 问答控制器
 * <p>提供基于知识库文档的 AI 问答接口，支持同步阻塞和流式（SSE）两种响应模式，
 * 以及会话记录的增删改查。所有接口均通过 {@code @PreAuthorize} 进行空间级别的权限校验。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/qa")
@RequiredArgsConstructor
public class QnAController {

    private final QnAService qnAService;
    private final AgenticQnAService agenticQnAService;
    private final QaChatSessionService sessionService;

    // ---- 问答接口 ----

    /**
     * 同步 RAG 问答
     * <p>检索知识库相关文档片段后调用 LLM 生成完整答案，一次性返回结果。
     * 传入 {@code sessionId} 可延续多轮对话历史，每次问答自动持久化会话记录。</p>
     *
     * @param spaceId 空间 ID
     * @param req     问答请求体，包含 question、sessionId（可选）、modelProvider（可选）、topK
     * @return 完整的问答响应，含答案正文和引用来源列表
     */
    @PostMapping("/ask/advanced")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<QnAResponse>> ask(
            @PathVariable UUID spaceId,
            @Valid @RequestBody QnARequest req) {
        return ResponseEntity.ok(ApiResponse.ok(qnAService.ask(spaceId, req)));
    }

    /**
     * Agentic RAG 问答
     * <p>LLM 作为 Agent 自主决定检索策略，通过 searchKnowledgeBase 工具进行多轮检索，
     * 适合复杂问题、多跳推理等场景。每次问答自动持久化会话记录。</p>
     *
     * @param spaceId 空间 ID
     * @param req     问答请求体
     * @return 问答响应，含 Agent 综合多轮检索后生成的答案及所有引用来源
     */
    @PostMapping("/ask")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public ResponseEntity<ApiResponse<QnAResponse>> askAgentic(
            @PathVariable UUID spaceId,
            @Valid @RequestBody QnARequest req) {
        return ResponseEntity.ok(ApiResponse.ok(agenticQnAService.ask(spaceId, req)));
    }

    /**
     * 流式 RAG 问答（Server-Sent Events）
     * <p>以 SSE 格式逐 token 推送 LLM 生成的答案，前端通过 {@code EventSource} 接收实现逐字显示效果。</p>
     *
     * @param spaceId 空间 ID
     * @param req     问答请求体
     * @return token 字符串的响应式流，Content-Type 为 {@code text/event-stream}
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public Flux<String> askStream(
            @PathVariable UUID spaceId,
            @Valid @RequestBody QnARequest req) {
        return qnAService.askStream(spaceId, req);
    }

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
