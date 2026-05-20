package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.ConversationState;
import com.enterprise.kb.search.dto.RoutingDecision;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Tier-1 域路由服务。
 *
 * <p>客服助手两层路由架构的第一层：把用户最新消息分类到某个业务域，
 * 再由 {@code DomainHandler} 在域内决定调用哪个工具。本服务只对"域"负责。
 *
 * <p>采用一次便宜模型调用完成分类，输入含最近若干轮上下文与当前域，
 * 输出含判定证据；摘不出证据时返回 {@code UNCLEAR} 触发反问，绝不猜测。
 */
public interface DomainRouterService {

    /**
     * 对用户最新消息做 Tier-1 域路由判定。
     *
     * @param history 对话历史，user / assistant 消息交替；可为空（新会话）
     * @param state   会话路由状态，提供"当前所处业务域"等上下文
     * @param message 用户最新消息正文
     * @return 路由判定结果；任何异常都降级为 {@code primaryDomain = UNCLEAR}，偏向反问而非误判域
     */
    RoutingDecision route(List<Message> history, ConversationState state, String message);
}
