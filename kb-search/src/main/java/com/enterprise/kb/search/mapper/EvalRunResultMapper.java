package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.EvalRunResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * 评估运行结果数据访问 Mapper。
 */
@Mapper
public interface EvalRunResultMapper {

    /**
     * 查询一次运行的所有用例结果。
     *
     * @param evalRunId 运行 ID
     * @return 结果列表
     */
    List<EvalRunResult> findByEvalRunId(@Param("evalRunId") UUID evalRunId);

    /**
     * 插入评估结果。
     *
     * @param result 结果实体
     */
    void insert(EvalRunResult result);
}
