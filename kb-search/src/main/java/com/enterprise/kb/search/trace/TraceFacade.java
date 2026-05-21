package com.enterprise.kb.search.trace;

/**
 * Trace 门面。
 */
public interface TraceFacade {

    /**
     * 开始一次 Trace。
     *
     * @param command 创建命令
     * @return Trace Scope
     */
    TraceScope start(TraceStartCommand command);

    /**
     * 记录事件。
     *
     * @param context Trace 上下文
     * @param event   Trace 事件
     */
    void recordEvent(TraceContext context, TraceEvent event);
}
