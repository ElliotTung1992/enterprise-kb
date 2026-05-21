package com.enterprise.kb.search.trace.agent;

import com.alibaba.cloud.ai.graph.agent.Builder;
import com.enterprise.kb.search.trace.TraceContext;

/**
 * 带 Trace 拦截器的 ReactAgent Builder 工厂。
 */
public interface TraceReactAgentFactory {

    /**
     * 创建已注入 Trace 拦截器的 Builder。
     *
     * @param agentName    Agent 名称
     * @param traceContext Trace 上下文
     * @return ReactAgent Builder
     */
    Builder builder(String agentName, TraceContext traceContext);
}
