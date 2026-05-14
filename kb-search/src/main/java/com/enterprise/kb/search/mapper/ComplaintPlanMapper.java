package com.enterprise.kb.search.mapper;

import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.search.model.ComplaintPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 投诉升级处理计划 Mapper
 */
@Mapper
public interface ComplaintPlanMapper {

    /**
     * 根据 ID 查询处理计划
     *
     * @param id 计划 ID
     * @return 处理计划
     */
    Optional<ComplaintPlan> findById(@Param("id") UUID id);

    /**
     * 查询投诉案件下的所有处理计划，按创建时间升序
     *
     * @param complaintId 投诉案件 ID
     * @return 处理计划列表
     */
    List<ComplaintPlan> findByComplaintId(@Param("complaintId") UUID complaintId);

    /**
     * 插入处理计划
     *
     * @param plan 处理计划
     */
    void insert(ComplaintPlan plan);

    /**
     * 更新处理计划状态
     *
     * @param id        计划 ID
     * @param status    新状态
     * @param updatedAt 更新时间
     */
    void updateStatus(@Param("id") UUID id,
                      @Param("status") ComplaintPlanStatus status,
                      @Param("updatedAt") Instant updatedAt);
}
