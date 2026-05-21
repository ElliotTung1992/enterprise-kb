package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.mapper.EvalCaseMapper;
import com.enterprise.kb.search.model.EvalCase;
import com.enterprise.kb.search.service.EvalCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评估用例服务实现。
 */
@Service
@RequiredArgsConstructor
public class EvalCaseServiceImpl implements EvalCaseService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final EvalCaseMapper evalCaseMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<EvalCase> findById(UUID id) {
        return evalCaseMapper.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvalCase> listEnabledByDataset(String dataset) {
        return evalCaseMapper.findEnabledByDataset(dataset);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<EvalCase> listCases(String dataset, String caseType, Boolean enabled, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return evalCaseMapper.findByFilters(dataset, caseType, enabled, safeLimit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvalCase create(EvalCase evalCase) {
        Instant now = Instant.now();
        if (evalCase.getId() == null) {
            evalCase.setId(UUID.randomUUID());
        }
        evalCase.setCreatedAt(now);
        evalCase.setUpdatedAt(now);
        evalCaseMapper.insert(evalCase);
        return evalCase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public EvalCase update(EvalCase evalCase) {
        evalCase.setUpdatedAt(Instant.now());
        evalCaseMapper.update(evalCase);
        return evalCase;
    }
}
