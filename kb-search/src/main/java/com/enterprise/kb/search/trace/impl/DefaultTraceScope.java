package com.enterprise.kb.search.trace.impl;

import com.enterprise.kb.search.trace.TraceContext;
import com.enterprise.kb.search.trace.TraceEvent;
import com.enterprise.kb.search.trace.TraceFacade;
import com.enterprise.kb.search.trace.TraceScope;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 默认 Trace Scope 实现。
 */
final class DefaultTraceScope implements TraceScope {

    private final TraceFacade traceFacade;
    private final TraceContext context;
    private final Instant startedAt;
    private final AtomicBoolean completed = new AtomicBoolean(false);

    DefaultTraceScope(TraceFacade traceFacade, TraceContext context, Instant startedAt) {
        this.traceFacade = traceFacade;
        this.context = context;
        this.startedAt = startedAt;
    }

    @Override
    public UUID traceId() {
        return context.traceId();
    }

    @Override
    public boolean enabled() {
        return context.enabled();
    }

    @Override
    public TraceContext context() {
        return context;
    }

    @Override
    public void event(TraceEvent event) {
        traceFacade.recordEvent(context, event);
    }

    @Override
    public void complete(Object output, Integer tokensUsed) {
        if (completed.compareAndSet(false, true) && traceFacade instanceof TraceScopeCompletion completion) {
            completion.complete(context, output, elapsedMs(), tokensUsed);
        }
    }

    @Override
    public void fail(Throwable error) {
        if (completed.compareAndSet(false, true) && traceFacade instanceof TraceScopeCompletion completion) {
            completion.fail(context, error, elapsedMs());
        }
    }

    @Override
    public void close() {
        complete(null, null);
    }

    private long elapsedMs() {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
