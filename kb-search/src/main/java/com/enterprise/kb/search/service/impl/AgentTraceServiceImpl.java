package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.mapper.AgentTraceMapper;
import com.enterprise.kb.search.mapper.AgentTraceStepMapper;
import com.enterprise.kb.search.model.AgentTrace;
import com.enterprise.kb.search.model.AgentTraceStep;
import com.enterprise.kb.search.service.AgentTraceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent Trace 查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class AgentTraceServiceImpl implements AgentTraceService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final AgentTraceMapper agentTraceMapper;
    private final AgentTraceStepMapper agentTraceStepMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<AgentTrace> findById(UUID id) {
        return agentTraceMapper.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<AgentTraceStep> listSteps(UUID traceId) {
        return agentTraceStepMapper.findByTraceId(traceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<AgentTrace> listTraces(UUID spaceId, UUID sessionId, String traceType, String status, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return agentTraceMapper.findByFilters(spaceId, sessionId, traceType, status, safeLimit);
    }
}
