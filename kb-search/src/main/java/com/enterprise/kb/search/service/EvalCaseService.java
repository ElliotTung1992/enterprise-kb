package com.enterprise.kb.search.service;

import com.enterprise.kb.search.model.EvalCase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评估用例服务。
 */
public interface EvalCaseService {

    /**
     * 根据 ID 查询评估用例。
     *
     * @param id 用例 ID
     * @return 用例实体
     */
    Optional<EvalCase> findById(UUID id);

    /**
     * 查询指定数据集下启用的评估用例。
     *
     * @param dataset 数据集名称
     * @return 用例列表
     */
    List<EvalCase> listEnabledByDataset(String dataset);

    /**
     * 创建评估用例。
     *
     * @param evalCase 用例实体
     * @return 已创建的用例
     */
    EvalCase create(EvalCase evalCase);

    /**
     * 更新评估用例。
     *
     * @param evalCase 用例实体
     * @return 已更新的用例
     */
    EvalCase update(EvalCase evalCase);
}
