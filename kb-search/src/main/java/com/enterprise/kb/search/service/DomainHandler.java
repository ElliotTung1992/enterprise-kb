package com.enterprise.kb.search.service;

import com.enterprise.kb.common.constants.Domain;
import com.enterprise.kb.search.dto.DomainContext;
import com.enterprise.kb.search.dto.DomainResult;

/**
 * 业务域处理器（Tier-2）。
 *
 * <p>每个已接入的业务域提供一个实现：域内部用什么编排（ReactAgent、StateGraph、
 * 规则）由实现自己决定，对 Tier-1 路由器完全黑盒。新增业务域 = 新增一个实现并注册，
 * 路由框架与流水线不需要改动。
 */
public interface DomainHandler {

    /**
     * 本处理器负责的业务域。
     *
     * @return 业务域枚举值
     */
    Domain domain();

    /**
     * 在本业务域内处理一轮用户消息。
     *
     * @param ctx 域内处理上下文
     * @return 处理结果，含回复文本与是否等待槽位回填
     */
    DomainResult handle(DomainContext ctx);
}
