package com.enterprise.kb.search.service;

import com.enterprise.kb.search.model.AgentTrace;
import com.enterprise.kb.search.model.AgentTraceStep;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent Trace 查询服务。
 */
public interface AgentTraceService {

    /**
     * 根据 ID 查询 Trace。
     *
     * @param id Trace ID
     * @return Trace 实体
     */
    Optional<AgentTrace> findById(UUID id);

    /**
     * 查询 Trace 步骤列表。
     *
     * @param traceId Trace ID
     * @return Step 列表
     */
    List<AgentTraceStep> listSteps(UUID traceId);

    /**
     * 按条件查询 Trace。
     *
     * @param spaceId   空间 ID
     * @param sessionId 会话 ID
     * @param traceType Trace 类型
     * @param status    状态
     * @param limit     返回条数
     * @return Trace 列表
     */
    List<AgentTrace> listTraces(UUID spaceId, UUID sessionId, String traceType, String status, int limit);
}
