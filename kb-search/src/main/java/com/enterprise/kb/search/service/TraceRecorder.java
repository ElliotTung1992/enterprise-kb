package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.TraceCompleteRequest;
import com.enterprise.kb.search.dto.TraceStartRequest;
import com.enterprise.kb.search.dto.TraceStepRequest;

import java.util.Optional;
import java.util.UUID;

/**
 * Agent Trace 轻量记录器。
 */
public interface TraceRecorder {

    /**
     * 创建 Trace 外壳。
     *
     * @param req 创建请求
     * @return Trace ID；配置关闭或未命中采样时为空
     */
    Optional<UUID> startTrace(TraceStartRequest req);

    /**
     * 记录异步 Step。
     *
     * @param traceId Trace ID
     * @param req     Step 请求
     */
    void recordStep(UUID traceId, TraceStepRequest req);

    /**
     * 同步记录 HITL 中断 Step。
     *
     * @param traceId Trace ID
     * @param req     Step 请求
     */
    void recordHitlInterrupt(UUID traceId, TraceStepRequest req);

    /**
     * 标记 Trace 成功完成。
     *
     * @param traceId Trace ID
     * @param req     完成请求
     */
    void completeTrace(UUID traceId, TraceCompleteRequest req);

    /**
     * 标记 Trace 失败。
     *
     * @param traceId    Trace ID
     * @param error      异常
     * @param durationMs 总耗时毫秒
     */
    void failTrace(UUID traceId, Throwable error, Long durationMs);
}
