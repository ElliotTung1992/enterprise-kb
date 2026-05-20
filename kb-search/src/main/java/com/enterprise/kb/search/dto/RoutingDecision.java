package com.enterprise.kb.search.dto;

import com.enterprise.kb.common.constants.Domain;

import java.util.List;

/**
 * Tier-1 域路由器的判定结果。
 *
 * @param primaryDomain 主域，对话的分派目标
 * @param secondary     同句中提及的次要业务域，主域处理完后反问；无则为空列表
 * @param runnerUp      次可能的域，用于 UNCLEAR 判定时的证据对照，可为 {@code null}
 * @param evidence      判定主域所依据的、从用户原话摘出的证据短语；摘不出具体证据时主域应为 {@code UNCLEAR}
 * @param emotional     用户最新消息是否为带明显负面情绪的宣泄（愤怒/失望/讽刺）；
 *                      情绪本身不切换域，但会累积，达阈值时主动反问是否升级投诉
 */
public record RoutingDecision(
        Domain primaryDomain,
        List<Domain> secondary,
        Domain runnerUp,
        String evidence,
        boolean emotional
) {

    /** 创建一个无次要意图、非情绪的判定结果。 */
    public static RoutingDecision of(Domain primaryDomain, Domain runnerUp, String evidence) {
        return new RoutingDecision(primaryDomain, List.of(), runnerUp, evidence, false);
    }
}
