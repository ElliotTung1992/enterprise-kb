package com.enterprise.kb.search.trace;

/**
 * 空 Trace 门面。
 */
public enum NoopTraceFacade implements TraceFacade {
    /** 单例 */
    INSTANCE;

    @Override
    public TraceScope start(TraceStartCommand command) {
        return NoopTraceScope.INSTANCE;
    }

    @Override
    public void recordEvent(TraceContext context, TraceEvent event) {
    }
}
