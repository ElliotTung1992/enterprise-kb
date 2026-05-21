package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.model.AgentTrace;
import com.enterprise.kb.search.model.AgentTraceStep;
import com.enterprise.kb.search.model.EvalCase;
import com.enterprise.kb.search.service.AgentTraceService;
import com.enterprise.kb.search.service.EvalCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent Trace 管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/traces")
@RequiredArgsConstructor
public class AgentTraceController {

    private final AgentTraceService agentTraceService;
    private final EvalCaseService evalCaseService;

    /**
     * 查询 Trace 列表。
     *
     * @param spaceId   空间 ID，可为空
     * @param sessionId 会话 ID，可为空
     * @param traceType Trace 类型，可为空
     * @param status    状态，可为空
     * @param limit     返回条数
     * @return Trace 列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<AgentTrace>>> list(
            @RequestParam(required = false) UUID spaceId,
            @RequestParam(required = false) UUID sessionId,
            @RequestParam(required = false) String traceType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(
                agentTraceService.listTraces(spaceId, sessionId, traceType, status, limit)));
    }

    /**
     * 查询 Trace 详情。
     *
     * @param traceId Trace ID
     * @return Trace 与步骤列表
     */
    @GetMapping("/{traceId}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable UUID traceId) {
        AgentTrace trace = findTrace(traceId);
        return ResponseEntity.ok(ApiResponse.ok(traceBundle(trace)));
    }

    /**
     * 导出离线复现包。
     *
     * @param traceId Trace ID
     * @return replay JSON
     */
    @GetMapping("/{traceId}/replay")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> replay(@PathVariable UUID traceId) {
        AgentTrace trace = findTrace(traceId);
        Map<String, Object> replay = traceBundle(trace);
        replay.put("schemaVersion", "agent-trace-replay/v1");
        replay.put("replayMode", "MOCK_ONLY");
        replay.put("sideEffects", "DISABLED");
        return ResponseEntity.ok(ApiResponse.ok(replay));
    }

    /**
     * 从 Trace 生成评估用例草稿。
     *
     * @param traceId Trace ID
     * @param req     草稿参数
     * @return 已创建的评估用例
     */
    @PostMapping("/{traceId}/eval-case")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<EvalCase>> createEvalCase(
            @PathVariable UUID traceId,
            @RequestBody(required = false) CreateEvalCaseRequest req) {
        AgentTrace trace = findTrace(traceId);
        CreateEvalCaseRequest request = req != null ? req : new CreateEvalCaseRequest(null, null, null);
        EvalCase evalCase = new EvalCase();
        evalCase.setSourceTraceId(trace.getId());
        evalCase.setCaseType(request.caseType() != null ? request.caseType() : inferCaseType(trace));
        evalCase.setName(request.name() != null ? request.name() : "Trace " + trace.getId());
        evalCase.setDataset(request.dataset() != null ? request.dataset() : "draft");
        evalCase.setInputJson(trace.getRawInputJson() != null ? trace.getRawInputJson() : "{}");
        evalCase.setExpectedJson("{\"mustContain\":[]}");
        evalCase.setMockOutputsJson("{}");
        evalCase.setEnabled(false);
        evalCase.setCreatedBy(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(evalCaseService.create(evalCase)));
    }

    private AgentTrace findTrace(UUID traceId) {
        return agentTraceService.findById(traceId)
                .orElseThrow(() -> new KbException("Trace 不存在", HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> traceBundle(AgentTrace trace) {
        List<AgentTraceStep> steps = agentTraceService.listSteps(trace.getId());
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("trace", trace);
        bundle.put("steps", steps);
        bundle.put("input", trace.getRawInputJson());
        bundle.put("output", trace.getRawOutputJson());
        return bundle;
    }

    private String inferCaseType(AgentTrace trace) {
        return switch (trace.getTraceType()) {
            case "STANDARD_QA", "AGENTIC_QA" -> "ANSWER";
            case "CUSTOMER_ASSISTANT", "CUSTOMER_ASSISTANT_ROUTED", "CUSTOMER_ASSISTANT_LEGACY" -> "ROUTING_TOOL";
            default -> "END_TO_END";
        };
    }

    public record CreateEvalCaseRequest(String dataset, String caseType, String name) {}
}
