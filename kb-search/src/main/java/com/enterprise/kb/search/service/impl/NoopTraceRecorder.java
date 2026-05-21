package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.TraceCompleteRequest;
import com.enterprise.kb.search.dto.TraceStartRequest;
import com.enterprise.kb.search.dto.TraceStepRequest;
import com.enterprise.kb.search.service.TraceRecorder;

import java.util.Optional;
import java.util.UUID;

/**
 * 单测兼容用空 TraceRecorder。
 */
final class NoopTraceRecorder implements TraceRecorder {

    @Override
    public Optional<UUID> startTrace(TraceStartRequest req) {
        return Optional.empty();
    }

    @Override
    public void recordStep(UUID traceId, TraceStepRequest req) {
    }

    @Override
    public void recordHitlInterrupt(UUID traceId, TraceStepRequest req) {
    }

    @Override
    public void completeTrace(UUID traceId, TraceCompleteRequest req) {
    }

    @Override
    public void failTrace(UUID traceId, Throwable error, Long durationMs) {
    }
}
