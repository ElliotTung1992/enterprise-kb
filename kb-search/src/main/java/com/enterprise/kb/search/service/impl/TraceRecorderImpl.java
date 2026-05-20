package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.config.TraceProperties;
import com.enterprise.kb.search.dto.TraceCompleteRequest;
import com.enterprise.kb.search.dto.TraceStartRequest;
import com.enterprise.kb.search.dto.TraceStepRequest;
import com.enterprise.kb.search.mapper.AgentTraceMapper;
import com.enterprise.kb.search.mapper.AgentTraceStepMapper;
import com.enterprise.kb.search.model.AgentTrace;
import com.enterprise.kb.search.model.AgentTraceStep;
import com.enterprise.kb.search.service.TraceRecorder;
import com.enterprise.kb.search.service.TraceRedactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Agent Trace 轻量记录器实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceRecorderImpl implements TraceRecorder {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";

    private final AgentTraceMapper agentTraceMapper;
    private final AgentTraceStepMapper agentTraceStepMapper;
    private final TraceProperties traceProperties;
    private final TraceRedactionService traceRedactionService;
    @Qualifier("traceExecutor")
    private final Executor traceExecutor;

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<UUID> startTrace(TraceStartRequest req) {
        if (!shouldRecord()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Payload rawInput = preparePayload(req.rawInputJson());

        AgentTrace trace = new AgentTrace();
        trace.setId(UUID.randomUUID());
        trace.setTraceType(req.traceType());
        trace.setSessionId(req.sessionId());
        trace.setSpaceId(req.spaceId());
        trace.setUserId(req.userId());
        trace.setRequestId(req.requestId());
        trace.setModelProvider(req.modelProvider());
        trace.setStatus(STATUS_RUNNING);
        trace.setInputText(req.inputText());
        trace.setRawInputJson(rawInput.value());
        trace.setPayloadTruncated(rawInput.truncated());
        trace.setStartedAt(now);
        trace.setCreatedAt(now);

        try {
            agentTraceMapper.insert(trace);
            return Optional.of(trace.getId());
        } catch (Exception e) {
            log.warn("创建 Agent Trace 失败：traceType={}，sessionId={}", req.traceType(), req.sessionId(), e);
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordStep(UUID traceId, TraceStepRequest req) {
        if (traceId == null || !traceProperties.isEnabled()) {
            return;
        }
        traceExecutor.execute(() -> insertStep(traceId, req));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordHitlInterrupt(UUID traceId, TraceStepRequest req) {
        if (traceId == null || !traceProperties.isEnabled()) {
            return;
        }
        insertStep(traceId, req);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void completeTrace(UUID traceId, TraceCompleteRequest req) {
        if (traceId == null || !traceProperties.isEnabled()) {
            return;
        }
        Payload rawOutput = preparePayload(req.rawOutputJson());
        try {
            agentTraceMapper.complete(traceId, STATUS_SUCCEEDED, req.outputText(), rawOutput.value(),
                    req.durationMs(), req.tokensUsed(), rawOutput.truncated(), Instant.now());
        } catch (Exception e) {
            log.warn("完成 Agent Trace 失败：traceId={}", traceId, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void failTrace(UUID traceId, Throwable error, Long durationMs) {
        if (traceId == null || !traceProperties.isEnabled()) {
            return;
        }
        try {
            agentTraceMapper.fail(traceId, STATUS_FAILED,
                    error != null ? error.getClass().getName() : null,
                    error != null ? error.getMessage() : null,
                    durationMs, Instant.now());
        } catch (Exception e) {
            log.warn("标记 Agent Trace 失败状态失败：traceId={}", traceId, e);
        }
    }

    private void insertStep(UUID traceId, TraceStepRequest req) {
        Instant now = Instant.now();
        Payload input = preparePayload(req.inputJson());
        Payload output = preparePayload(req.outputJson());
        Payload raw = preparePayload(req.rawPayloadJson());

        AgentTraceStep step = new AgentTraceStep();
        step.setId(UUID.randomUUID());
        step.setTraceId(traceId);
        step.setStepIndex(req.stepIndex());
        step.setStepType(req.stepType());
        step.setAgentName(req.agentName());
        step.setToolCallId(req.toolCallId());
        step.setToolName(req.toolName());
        step.setStatus(req.status());
        step.setInputJson(input.value());
        step.setOutputJson(output.value());
        step.setRawPayloadJson(traceProperties.isFullRawEnabled() ? raw.value() : null);
        step.setBusinessRefType(req.businessRefType());
        step.setBusinessRefId(req.businessRefId());
        step.setDurationMs(req.durationMs());
        step.setErrorType(req.errorType());
        step.setErrorMessage(req.errorMessage());
        step.setPayloadTruncated(input.truncated() || output.truncated() || raw.truncated());
        step.setCreatedAt(now);

        try {
            agentTraceStepMapper.insert(step);
        } catch (Exception e) {
            log.warn("写入 Agent Trace Step 失败：traceId={}，stepIndex={}，stepType={}",
                    traceId, req.stepIndex(), req.stepType(), e);
        }
    }

    private boolean shouldRecord() {
        if (!traceProperties.isEnabled()) {
            return false;
        }
        double sampleRate = traceProperties.getSampleRate();
        return sampleRate >= 1.0 || ThreadLocalRandom.current().nextDouble() < sampleRate;
    }

    private Payload preparePayload(String json) {
        if (json == null || json.isBlank()) {
            return new Payload(json, false);
        }
        String redacted = traceRedactionService.redactJson(json);
        byte[] bytes = redacted.getBytes(StandardCharsets.UTF_8);
        int maxBytes = traceProperties.getMaxPayloadBytes();
        if (maxBytes <= 0 || bytes.length <= maxBytes) {
            return new Payload(redacted, false);
        }

        String preview = new String(bytes, 0, Math.min(bytes.length, maxBytes), StandardCharsets.UTF_8);
        String escaped = preview
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return new Payload("{\"truncated\":true,\"preview\":\"" + escaped + "\"}", true);
    }

    private record Payload(String value, boolean truncated) {}
}
