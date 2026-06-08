package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.search.dto.QnARequest;
import com.enterprise.kb.search.dto.QnAResponse;
import com.enterprise.kb.search.service.MdAgenticQnAService;
import com.enterprise.kb.search.service.MdQnAService;
import com.enterprise.kb.search.tracing.QaObserved;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Markdown 结构感知问答控制器。
 */
@RestController
@RequestMapping("/api/v1/spaces/{spaceId}/md-qa")
@RequiredArgsConstructor
public class MdQnAController {

    private final MdQnAService mdQnAService;
    private final MdAgenticQnAService mdAgenticQnAService;

    /**
     * Markdown 结构感知同步问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return 问答响应
     */
    @PostMapping("/ask")
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    @QaObserved(name = "kb.qa.ask")
    public ResponseEntity<ApiResponse<QnAResponse>> ask(
            @PathVariable UUID spaceId,
            @Valid @RequestBody QnARequest req) {
        return ResponseEntity.ok(ApiResponse.ok(mdQnAService.ask(spaceId, req)));
    }

    /**
     * Markdown Agentic 两工具流式问答。
     *
     * @param spaceId 空间 ID
     * @param req     问答请求
     * @return token 流
     */
    @PostMapping(value = "/ask/agentic/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasPermission(#spaceId, 'SPACE', 'VIEWER')")
    @QaObserved(name = "kb.qa.ask.agentic.stream", eventStream = true)
    public Flux<String> askAgenticStream(
            @PathVariable UUID spaceId,
            @Valid @RequestBody QnARequest req) {
        return mdAgenticQnAService.askStream(spaceId, req);
    }
}
