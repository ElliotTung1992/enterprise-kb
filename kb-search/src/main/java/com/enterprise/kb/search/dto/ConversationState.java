package com.enterprise.kb.search.dto;

import com.enterprise.kb.common.constants.Domain;

/**
 * 会话级路由状态。
 *
 * <p>由 {@code ConversationStateStore} 持久化到 Redis，作为 Tier-1 路由器的输入态。
 *
 * @param currentDomain  当前对话所处的业务域；首轮或未路由时为 {@code null}
 * @param awaitingSlot   上一轮子 Agent 是否因缺槽位向用户反问；为 {@code true} 时下一轮跳过路由
 * @param emotionStrikes 同一会话内累积的纯情绪信号次数，达阈值时主动反问是否升级投诉
 * @param pendingOffer   上一轮主动反问"是否需要处理"的业务域（次要意图或情绪升级提议）；
 *                       为路由器提供上下文，使其能识别用户对该提议的肯定/否定回应。无则为 {@code null}
 */
public record ConversationState(Domain currentDomain, boolean awaitingSlot,
                                 int emotionStrikes, Domain pendingOffer) {

    /** 新会话的初始状态。 */
    public static ConversationState initial() {
        return new ConversationState(null, false, 0, null);
    }
}
