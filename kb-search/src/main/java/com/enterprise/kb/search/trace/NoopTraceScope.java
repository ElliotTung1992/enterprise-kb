package com.enterprise.kb.search.trace;

import java.util.UUID;

/**
 * 空 Trace Scope。
 */
public enum NoopTraceScope implements TraceScope {
    /** 单例 */
    INSTANCE;

    @Override
    public UUID traceId() {
        return null;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public TraceContext context() {
        return TraceContext.noop();
    }

    @Override
    public void event(TraceEvent event) {
    }

    @Override
    public void complete(Object output, Integer tokensUsed) {
    }

    @Override
    public void fail(Throwable error) {
    }

    @Override
    public void close() {
    }
}
