package com.enterprise.kb.search.trace.impl;

import com.enterprise.kb.search.trace.TraceContext;

/**
 * Trace Scope 完成回调。
 */
interface TraceScopeCompletion {

    /**
     * 标记成功。
     *
     * @param context    Trace 上下文
     * @param output     输出对象
     * @param durationMs 耗时毫秒
     * @param tokensUsed Token 用量
     */
    void complete(TraceContext context, Object output, Long durationMs, Integer tokensUsed);

    /**
     * 标记失败。
     *
     * @param context    Trace 上下文
     * @param error      异常
     * @param durationMs 耗时毫秒
     */
    void fail(TraceContext context, Throwable error, Long durationMs);
}
