package com.enterprise.kb.search.mapper;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.search.model.ComplaintPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
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

    /**
     * 按状态查询处理计划，status 为 null 时返回全部，按创建时间降序
     *
     * @param status 计划状态（可为 null）
     * @return 处理计划列表
     */
    List<ComplaintPlan> findByStatus(@Param("status") ComplaintPlanStatus status);

    /**
     * 将计划置为执行中，同时记录 deadline 检查时间
     *
     * @param id          计划 ID
     * @param nextCheckAt 商家响应 deadline（now + 48h）
     * @param updatedAt   更新时间
     */
    void startExecution(@Param("id") UUID id,
                        @Param("nextCheckAt") Instant nextCheckAt,
                        @Param("updatedAt") Instant updatedAt);

    /**
     * 查询所有 EXECUTING 且 next_check_at 已到期的计划
     *
     * @param now 当前时间
     * @return 超时计划列表
     */
    List<ComplaintPlan> findExecutingPastDeadline(@Param("now") Instant now);

    /**
     * 更新处理计划的可修改字段（责任方、补偿类型、补偿金额），null 参数不更新
     *
     * @param id                 计划 ID
     * @param responsibleParty   修改后的责任方（可为 null）
     * @param compensationType   修改后的补偿类型（可为 null）
     * @param compensationAmount 修改后的补偿金额（可为 null）
     * @param updatedAt          更新时间
     */
    void updateFields(@Param("id") UUID id,
                      @Param("responsibleParty") ResponsibleParty responsibleParty,
                      @Param("compensationType") CompensationType compensationType,
                      @Param("compensationAmount") BigDecimal compensationAmount,
                      @Param("updatedAt") Instant updatedAt);
}
