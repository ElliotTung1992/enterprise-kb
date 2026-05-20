package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.EvalRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 评估运行数据访问 Mapper。
 */
@Mapper
public interface EvalRunMapper {

    /**
     * 根据 ID 查询评估运行。
     *
     * @param id 运行 ID
     * @return 运行实体
     */
    Optional<EvalRun> findById(@Param("id") UUID id);

    /**
     * 插入评估运行。
     *
     * @param evalRun 运行实体
     */
    void insert(EvalRun evalRun);

    /**
     * 更新评估运行完成状态。
     */
    void complete(@Param("id") UUID id,
                  @Param("status") String status,
                  @Param("summaryJson") String summaryJson,
                  @Param("completedAt") Instant completedAt);
}
