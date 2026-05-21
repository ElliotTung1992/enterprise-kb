package com.enterprise.kb.search.trace;

/**
 * Trace 上下文持有器。
 */
public final class TraceContextHolder {

    private static final ThreadLocal<TraceScope> CURRENT_SCOPE = new ThreadLocal<>();

    private TraceContextHolder() {
    }

    /**
     * 获取当前 Trace 上下文。
     *
     * @return 当前上下文，未绑定时返回 noop
     */
    public static TraceContext currentOrNoop() {
        TraceScope scope = CURRENT_SCOPE.get();
        return scope == null ? TraceContext.noop() : scope.context();
    }

    /**
     * 获取当前 Trace Scope。
     *
     * @return 当前 Scope，未绑定时返回 noop
     */
    public static TraceScope currentScopeOrNoop() {
        TraceScope scope = CURRENT_SCOPE.get();
        return scope == null ? NoopTraceScope.INSTANCE : scope;
    }

    /**
     * 绑定当前线程 Trace Scope。
     *
     * @param scope Trace Scope
     * @return 关闭时恢复上一个 Scope 的句柄
     */
    public static AutoCloseable bind(TraceScope scope) {
        TraceScope previous = CURRENT_SCOPE.get();
        CURRENT_SCOPE.set(scope == null ? NoopTraceScope.INSTANCE : scope);
        return () -> {
            if (previous == null) {
                CURRENT_SCOPE.remove();
            } else {
                CURRENT_SCOPE.set(previous);
            }
        };
    }
}
