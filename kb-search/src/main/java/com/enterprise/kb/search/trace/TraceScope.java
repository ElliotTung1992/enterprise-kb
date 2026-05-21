package com.enterprise.kb.search.trace;

import java.util.UUID;

/**
 * 一次 Trace 生命周期。
 */
public interface TraceScope extends AutoCloseable {

    /**
     * Trace ID。
     *
     * @return Trace ID
     */
    UUID traceId();

    /**
     * 是否启用。
     *
     * @return true 表示启用
     */
    boolean enabled();

    /**
     * Trace 上下文。
     *
     * @return Trace 上下文
     */
    TraceContext context();

    /**
     * 记录事件。
     *
     * @param event 事件
     */
    void event(TraceEvent event);

    /**
     * 标记成功完成。
     *
     * @param output     输出对象
     * @param tokensUsed Token 用量
     */
    void complete(Object output, Integer tokensUsed);

    /**
     * 标记失败。
     *
     * @param error 异常
     */
    void fail(Throwable error);

    /**
     * 关闭 Scope。
     */
    @Override
    void close();
}
