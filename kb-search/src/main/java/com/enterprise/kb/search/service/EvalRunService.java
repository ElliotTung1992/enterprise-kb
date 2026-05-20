package com.enterprise.kb.search.service;

import com.enterprise.kb.search.model.EvalRun;
import com.enterprise.kb.search.model.EvalRunResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评估运行服务。
 */
public interface EvalRunService {

    /**
     * 根据 ID 查询评估运行。
     *
     * @param id 运行 ID
     * @return 运行实体
     */
    Optional<EvalRun> findById(UUID id);

    /**
     * 查询一次运行的逐用例结果。
     *
     * @param evalRunId 运行 ID
     * @return 结果列表
     */
    List<EvalRunResult> listResults(UUID evalRunId);

    /**
     * 创建评估运行。
     *
     * @param evalRun 运行实体
     * @return 已创建的运行
     */
    EvalRun create(EvalRun evalRun);

    /**
     * 保存单个评估结果。
     *
     * @param result 结果实体
     * @return 已保存的结果
     */
    EvalRunResult saveResult(EvalRunResult result);
}
