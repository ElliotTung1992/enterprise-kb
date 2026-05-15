package com.enterprise.kb.search.model;

import com.enterprise.kb.common.constants.CompensationType;
import com.enterprise.kb.common.constants.ComplaintPlanStatus;
import com.enterprise.kb.common.constants.ResponsibleParty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class ComplaintPlan {

    private UUID id;
    /** 关联投诉案件 ID */
    private UUID complaintId;
    /** 责任方 */
    private ResponsibleParty responsibleParty;
    /** 责任认定理由，供审核员查看 */
    private String responsibilityReason;
    /** 补偿类型 */
    private CompensationType compensationType;
    /** 补偿金额 */
    private BigDecimal compensationAmount;
    /** 联系顺序（JSONB 数组） */
    private String contactSequence;
    /** 超时策略（JSONB 对象） */
    private String timeouts;
    /** 备选方案（JSONB 数组） */
    private String fallbackPlan;
    /** 非列字段，由 JOIN complaints 查询填充 */
    private String orderId;
    /** 已重规划次数 */
    private int replanCount;
    /** 计划状态 */
    private ComplaintPlanStatus status;
    /** 下次 deadline 检查时间，进入 EXECUTING 时由 Executor 写入（now + 48h） */
    private Instant nextCheckAt;
    /** 创建时间 */
    private Instant createdAt;
    /** 最后更新时间 */
    private Instant updatedAt;
}
