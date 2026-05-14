package com.enterprise.kb.search.dto;

import java.math.BigDecimal;

/**
 * 投诉升级 Planner 的数据收集结果。
 *
 * @param complaintHistory 投诉历史
 * @param orderDetails 订单详情
 * @param logisticsTrace 物流轨迹
 * @param merchantViolationRecord 商家违规记录
 * @param compensationPolicy 赔偿政策
 */
public record ComplaintPlanningContext(
        ComplaintHistory complaintHistory,
        OrderDetails orderDetails,
        LogisticsTrace logisticsTrace,
        MerchantViolationRecord merchantViolationRecord,
        CompensationPolicy compensationPolicy
) {
    /**
     * 投诉历史摘要。
     *
     * @param complaintCount 历史投诉次数
     * @param unresolvedCount 未解决投诉次数
     */
    public record ComplaintHistory(int complaintCount, int unresolvedCount) {}

    /**
     * 订单详情摘要。
     *
     * @param orderId 订单号
     * @param amount 订单金额
     * @param merchantId 商家 ID
     * @param trackingNo 物流单号
     * @param status 订单状态
     */
    public record OrderDetails(String orderId, BigDecimal amount, String merchantId,
                               String trackingNo, String status) {}

    /**
     * 物流轨迹摘要。
     *
     * @param delivered 是否已签收
     * @param interruptedOver48h 物流轨迹是否中断超过 48 小时
     * @param deliveredWithin7Days 是否签收 7 天内
     */
    public record LogisticsTrace(boolean delivered, boolean interruptedOver48h,
                                 boolean deliveredWithin7Days) {}

    /**
     * 商家违规记录摘要。
     *
     * @param merchantId 商家 ID
     * @param violationCount 历史违规次数
     */
    public record MerchantViolationRecord(String merchantId, int violationCount) {}

    /**
     * 平台赔偿政策摘要。
     *
     * @param maxRefundAmount 最大退款金额
     * @param maxCouponAmount 最大优惠券补偿金额
     */
    public record CompensationPolicy(BigDecimal maxRefundAmount, BigDecimal maxCouponAmount) {}
}
