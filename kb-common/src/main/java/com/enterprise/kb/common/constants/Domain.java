package com.enterprise.kb.common.constants;

/**
 * 客服助手意图识别的业务域。
 *
 * <p>Tier-1 域路由器（{@code DomainRouterService}）的输出空间：已接入的业务域
 * 加三个特殊路由出口。新增业务域时在此追加枚举值，并提供对应的 DomainHandler 实现。
 *
 * <p>注意：评估集中还存在 {@code SKIP} 标签（表示该轮命中 awaiting_slot 硬规则、
 * 应跳过路由），但 {@code SKIP} 不是路由器的输出，故不在此枚举中。
 */
public enum Domain {

    /** 售后域：退款、换货等普通售后咨询与申请 */
    AFTER_SALES,

    /** 投诉域：复杂投诉升级，生成处理计划交专员审核 */
    COMPLAINT,

    /** 业务内但对应域尚未接入，应转人工坐席 */
    HANDOFF,

    /** 真域外闲聊，礼貌挡回并引导回业务 */
    CHITCHAT,

    /** 信息不足无法判定业务域，触发反问澄清 */
    UNCLEAR;

    /**
     * 是否为真实业务域。
     *
     * @return {@code true} 表示已接入的业务域；{@code false} 表示 HANDOFF / CHITCHAT / UNCLEAR 等特殊路由出口
     */
    public boolean isBusinessDomain() {
        return this == AFTER_SALES || this == COMPLAINT;
    }
}
