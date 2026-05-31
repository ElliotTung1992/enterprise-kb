package com.enterprise.kb.tracing;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * 在线 LLM Tracing 装配（ADR-015）。
 *
 * <p>整个配置仅在 {@code enterprise.kb.tracing.enabled=true} 时生效，关闭时不注册任何 tracing
 * 增强，应用照常启动（LangFuse 未起也不受影响）。装配内容：</p>
 * <ul>
 *   <li><b>①</b> reactor 自动上下文传播开关，使根 Observation 与 {@link com.enterprise.kb.common.tracing.TracingContextHolder}
 *       顺 reactor context 流进 graph-core 内部线程（覆盖 LLM span）；</li>
 *   <li>{@link SensitiveDataObservationFilter}：进 trace 高基数 tag 的脱敏 + 截断兜底；</li>
 *   <li>{@link LangfuseChildAttributeSpanProcessor}：把业务根 span 的 LangFuse 属性复制到子 span。</li>
 * </ul>
 *
 * <p>OTLP 导出器、采样器、{@code BatchSpanProcessor} 由 Spring Boot 的 OTLP tracing 自动配置依据
 * {@code management.otlp.tracing.*} / {@code management.tracing.*} 装配，本类不重复声明。</p>
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TracingProperties.class)
@ConditionalOnProperty(prefix = "enterprise.kb.tracing", name = "enabled", havingValue = "true")
public class TracingConfig {

    private final TracingProperties properties;

    public TracingConfig(TracingProperties properties) {
        this.properties = properties;
    }

    /**
     * ① 开启 reactor 全局自动上下文传播，使 thread-local（含 tracing 上下文与业务 holder）
     * 顺 reactor context 跨线程流动。全局副作用，仅在 tracing 开启时启用。
     */
    @PostConstruct
    public void enableReactorContextPropagation() {
        Hooks.enableAutomaticContextPropagation();
        log.info("【Tracing】已启用 reactor 自动上下文传播（ADR-015 ①）");
    }

    /**
     * 进 trace 高基数 tag 的脱敏 + 截断兜底过滤器。
     *
     * @return ObservationFilter
     */
    @Bean
    public SensitiveDataObservationFilter sensitiveDataObservationFilter() {
        int maxChars = Math.max(properties.getMaxPromptChars(), properties.getMaxCompletionChars());
        return new SensitiveDataObservationFilter(maxChars);
    }

    /**
     * 把业务根 span 的 LangFuse 业务属性复制到同一 trace 的每个子 span。
     * 作为 {@code SpanProcessor} bean 被 Spring Boot OTel 自动配置收集进 SdkTracerProvider。
     *
     * @return SpanProcessor
     */
    @Bean
    public LangfuseChildAttributeSpanProcessor langfuseChildAttributeSpanProcessor() {
        return new LangfuseChildAttributeSpanProcessor();
    }
}
