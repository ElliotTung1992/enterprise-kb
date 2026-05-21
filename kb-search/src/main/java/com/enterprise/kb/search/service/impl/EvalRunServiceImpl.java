package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.mapper.EvalRunMapper;
import com.enterprise.kb.search.mapper.EvalRunResultMapper;
import com.enterprise.kb.search.model.EvalRun;
import com.enterprise.kb.search.model.EvalRunResult;
import com.enterprise.kb.search.service.EvalRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评估运行服务实现。
 */
@Service
@RequiredArgsConstructor
public class EvalRunServiceImpl implements EvalRunService {

    private final EvalRunMapper evalRunMapper;
    private final EvalRunResultMapper evalRunResultMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<EvalRun> findById(UUID id) {
        return evalRunMapper.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvalRunResult> listResults(UUID evalRunId) {
        return evalRunResultMapper.findByEvalRunId(evalRunId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvalRun create(EvalRun evalRun) {
        Instant now = Instant.now();
        if (evalRun.getId() == null) {
            evalRun.setId(UUID.randomUUID());
        }
        evalRun.setStartedAt(now);
        evalRun.setCreatedAt(now);
        evalRunMapper.insert(evalRun);
        return evalRun;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvalRunResult saveResult(EvalRunResult result) {
        if (result.getId() == null) {
            result.setId(UUID.randomUUID());
        }
        result.setCreatedAt(Instant.now());
        evalRunResultMapper.insert(result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void complete(UUID id, String status, String summaryJson) {
        evalRunMapper.complete(id, status, summaryJson, Instant.now());
    }
}
