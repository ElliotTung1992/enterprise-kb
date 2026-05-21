package com.enterprise.kb.search.trace.impl;

import com.enterprise.kb.search.dto.TraceCompleteRequest;
import com.enterprise.kb.search.dto.TraceStartRequest;
import com.enterprise.kb.search.dto.TraceStepRequest;
import com.enterprise.kb.search.service.TraceRecorder;
import com.enterprise.kb.search.trace.NoopTraceScope;
import com.enterprise.kb.search.trace.TraceContext;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceFacade;
import com.enterprise.kb.search.trace.TraceScope;
import com.enterprise.kb.search.trace.TraceStartCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trace 门面默认实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TraceFacadeImpl implements TraceFacade, TraceScopeCompletion {

    private final TraceRecorder traceRecorder;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<UUID, AtomicInteger> stepCounters = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public TraceScope start(TraceStartCommand command) {
        String rawInputJson = toJson(command.rawInput());
        Optional<UUID> traceId = traceRecorder.startTrace(new TraceStartRequest(
                command.traceType(),
                command.sessionId(),
                command.spaceId(),
                command.userId(),
                command.requestId(),
                command.modelProvider(),
                command.inputText(),
                rawInputJson));
        if (traceId.isEmpty()) {
            return NoopTraceScope.INSTANCE;
        }

        TraceContext context = new TraceContext(traceId.get(), command.traceType(), command.agentName(),
                command.sessionId(), command.spaceId(), command.userId(), true);
        stepCounters.put(context.traceId(), new AtomicInteger(0));
        return new DefaultTraceScope(this, context, Instant.now());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recordEvent(TraceContext context, TraceEvent event) {
        if (context == null || !context.enabled() || context.traceId() == null || event == null) {
            return;
        }

        int stepIndex = stepCounters
                .computeIfAbsent(context.traceId(), ignored -> new AtomicInteger(0))
                .incrementAndGet();
        try {
            traceRecorder.recordStep(context.traceId(), new TraceStepRequest(
                    stepIndex,
                    event.stepType(),
                    context.agentName(),
                    event.toolCallId(),
                    "TOOL_CALL".equals(event.stepType()) ? event.name() : null,
                    event.status(),
                    toJson(event.input()),
                    toJson(event.output()),
                    toJson(event.rawPayload()),
                    event.businessRefType(),
                    event.businessRefId(),
                    event.durationMs(),
                    errorType(event.error()),
                    errorMessage(event.error())));
        } catch (Exception e) {
            log.warn("记录 Trace 事件失败：traceId={}，stepType={}", context.traceId(), event.stepType(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void complete(TraceContext context, Object output, Long durationMs, Integer tokensUsed) {
        if (context == null || !context.enabled() || context.traceId() == null) {
            return;
        }
        try {
            traceRecorder.completeTrace(context.traceId(), new TraceCompleteRequest(
                    outputText(output), toJson(output), durationMs, tokensUsed));
        } finally {
            stepCounters.remove(context.traceId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fail(TraceContext context, Throwable error, Long durationMs) {
        if (context == null || !context.enabled() || context.traceId() == null) {
            return;
        }
        try {
            traceRecorder.failTrace(context.traceId(), error, durationMs);
        } finally {
            stepCounters.remove(context.traceId());
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Trace payload 序列化失败：type={}", value.getClass().getName(), e);
            return "{\"serializationError\":\"" + e.getClass().getName() + "\"}";
        }
    }

    private String outputText(Object output) {
        if (output == null) {
            return null;
        }
        return output instanceof String text ? text : toJson(output);
    }

    private String errorType(Throwable error) {
        return error == null ? null : error.getClass().getName();
    }

    private String errorMessage(Throwable error) {
        return error == null ? null : error.getMessage();
    }
}
