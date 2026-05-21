package com.enterprise.kb.search.trace.agent;

import com.alibaba.cloud.ai.graph.agent.Builder;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.enterprise.kb.search.trace.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 带 Trace 拦截器的 ReactAgent Builder 工厂实现。
 */
@Component
@RequiredArgsConstructor
public class TraceReactAgentFactoryImpl implements TraceReactAgentFactory {

    private final TraceToolInterceptor traceToolInterceptor;
    private final TraceModelInterceptor traceModelInterceptor;

    /**
     * {@inheritDoc}
     */
    @Override
    public Builder builder(String agentName, TraceContext traceContext) {
        return ReactAgent.builder()
                .name(agentName)
                .interceptors(traceModelInterceptor, traceToolInterceptor);
    }
}
