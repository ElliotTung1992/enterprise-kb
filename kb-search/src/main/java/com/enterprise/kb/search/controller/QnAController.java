package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.service.AgenticQnAService;
import com.enterprise.kb.search.service.QnAService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * RAG 问答控制器
 * <p>提供基于知识库文档的 AI 问答接口，支持同步阻塞和流式（SSE）两种响应模式。
 * 内部通过 {@link QnAService} 完成向量检索 + LLM 生成，答案附带引用来源信息。
 * 所有接口均通过 {@code @PreAuthorize} 进行空间级别的权限校验。</p>
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/qa")
@RequiredArgsConstructor
public class QnAController {

    private final QnAService qnAService;
    private final AgenticQnAService agenticQnAService;

    /**
     * 同步 RAG 问答
     * <p>检索知识库相关文档片段后调用 LLM 生成完整答案，一次性返回结果。
     * 响应包含 answer 文本及 citations 数组（文档标题、页码、原文摘录）。
     * 传入 {@code sessionId} 可延续多轮对话历史。</p>
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
     * 适合复杂问题、多跳推理等场景。citations 汇总所有轮次的检索结果。</p>
     *
     * @param spaceId 空间 ID
     * @param req     问答请求体，同 {@code /ask} 接口
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
     * <p>以 SSE 格式逐 token 推送 LLM 生成的答案，前端通过 {@code EventSource} 接收实现逐字显示效果。
     * 适用于长回答场景，降低用户感知延迟。</p>
     *
     * @param spaceId 空间 ID
     * @param req     问答请求体，同 {@code /ask} 接口
     * @return token 字符串的响应式流，Content-Type 为 {@code text/event-stream}
     */
    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    public Flux<String> askStream(
            @PathVariable UUID spaceId,
            @Valid @RequestBody QnARequest req) {
        return qnAService.askStream(spaceId, req);
    }
}
