package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.EvalCase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 评估用例数据访问 Mapper。
 */
@Mapper
public interface EvalCaseMapper {

    /**
     * 根据 ID 查询评估用例。
     *
     * @param id 用例 ID
     * @return 用例实体
     */
    Optional<EvalCase> findById(@Param("id") UUID id);

    /**
     * 按数据集查询启用的评估用例。
     *
     * @param dataset 数据集名称
     * @return 用例列表
     */
    List<EvalCase> findEnabledByDataset(@Param("dataset") String dataset);

    /**
     * 插入评估用例。
     *
     * @param evalCase 用例实体
     */
    void insert(EvalCase evalCase);

    /**
     * 更新评估用例。
     *
     * @param evalCase 用例实体
     */
    void update(EvalCase evalCase);
}
