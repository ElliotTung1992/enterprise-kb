package com.enterprise.kb.search.mapper;

import com.enterprise.kb.search.model.AgentTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Agent Trace 数据访问 Mapper。
 */
@Mapper
public interface AgentTraceMapper {

    /**
     * 根据 ID 查询 Trace。
     *
     * @param id Trace ID
     * @return Trace 实体
     */
    Optional<AgentTrace> findById(@Param("id") UUID id);

    /**
     * 按条件查询 Trace 列表。
     *
     * @param spaceId   空间 ID，可为空
     * @param sessionId 会话 ID，可为空
     * @param traceType Trace 类型，可为空
     * @param status    状态，可为空
     * @param limit     返回条数
     * @return Trace 列表
     */
    List<AgentTrace> findByFilters(@Param("spaceId") UUID spaceId,
                                   @Param("sessionId") UUID sessionId,
                                   @Param("traceType") String traceType,
                                   @Param("status") String status,
                                   @Param("limit") int limit);

    /**
     * 插入 Trace。
     *
     * @param trace Trace 实体
     */
    void insert(AgentTrace trace);

    /**
     * 标记 Trace 成功完成。
     */
    void complete(@Param("id") UUID id,
                  @Param("status") String status,
                  @Param("outputText") String outputText,
                  @Param("rawOutputJson") String rawOutputJson,
                  @Param("durationMs") Long durationMs,
                  @Param("tokensUsed") Integer tokensUsed,
                  @Param("payloadTruncated") boolean payloadTruncated,
                  @Param("completedAt") Instant completedAt);

    /**
     * 标记 Trace 执行失败。
     */
    void fail(@Param("id") UUID id,
              @Param("status") String status,
              @Param("errorType") String errorType,
              @Param("errorMessage") String errorMessage,
              @Param("durationMs") Long durationMs,
              @Param("completedAt") Instant completedAt);
}
