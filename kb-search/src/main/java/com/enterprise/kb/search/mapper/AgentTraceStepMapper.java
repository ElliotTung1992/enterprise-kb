package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.AgentTraceStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * Agent Trace Step 数据访问 Mapper。
 */
@Mapper
public interface AgentTraceStepMapper {

    /**
     * 查询指定 Trace 的步骤列表。
     *
     * @param traceId Trace ID
     * @return Step 列表
     */
    List<AgentTraceStep> findByTraceId(@Param("traceId") UUID traceId);

    /**
     * 插入 Step。
     *
     * @param step Step 实体
     */
    void insert(AgentTraceStep step);
}
