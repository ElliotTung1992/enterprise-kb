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
 *   <li>{@link ChatModelContentObservationFilter}：把 Spring AI ChatModel 的 prompt/completion
 *       映射到 LangFuse observation input/output；</li>
 *   <li>{@link VectorStoreContentObservationFilter}：把 Spring AI VectorStore 的查询请求 / 召回文档
 *       映射到 LangFuse observation input/output；</li>
 *   <li>{@link LangfuseChildAttributeSpanProcessor}：把业务根 span 的 LangFuse 属性复制到子 span；</li>
 *   <li>{@link ExcludedObservationNamePredicate}：基础设施 span 噪音黑名单过滤。</li>
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
     * 将 Spring AI ChatModel 的真实 prompt/completion 写入 LangFuse observation input/output。
     *
     * @return ObservationFilter
     */
    @Bean
    public ChatModelContentObservationFilter chatModelContentObservationFilter() {
        return new ChatModelContentObservationFilter(properties.getMaxPromptChars(), properties.getMaxCompletionChars());
    }

    /**
     * 把 Spring AI tool observation 的入参 / 返回写入 Spring AI 原生 key，并镜像到 LangFuse
     * observation input/output。内容在写入源头完成脱敏截断，不直接注册 Spring AI 原生正文 filter，
     * 避免未脱敏的 {@code spring.ai.tool.call.arguments/result} 进入 high-cardinality keys。
     *
     * @return ObservationFilter
     */
    @Bean
    public ToolCallingContentLangfuseObservationFilter toolCallingContentLangfuseObservationFilter() {
        return new ToolCallingContentLangfuseObservationFilter(properties.getMaxToolChars());
    }

    /**
     * 将 Spring AI VectorStore observation 的查询请求 / 召回文档写入 LangFuse observation input/output。
     * 补齐 ChatModel / Tool 之外缺失的 VectorStore（{@code milvus query}）这一类正文映射。
     *
     * @return ObservationFilter
     */
    @Bean
    public VectorStoreContentObservationFilter vectorStoreContentObservationFilter() {
        return new VectorStoreContentObservationFilter(properties.getMaxRetrievalChars());
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

    /**
     * 基础设施 span 噪音黑名单过滤 predicate（design-langfuse-noise-filter）。命中黑名单前缀的
     * observation 直接 NOOP，不生成 span。作为 {@code ObservationPredicate} bean 被 Spring Boot
     * 的 {@code ObservationAutoConfiguration} 自动收集进 {@code ObservationRegistry}。
     *
     * @return ObservationPredicate
     */
    @Bean
    public ExcludedObservationNamePredicate excludedObservationNamePredicate() {
        return new ExcludedObservationNamePredicate(properties.getExcludedObservationNamePrefixes());
    }
}
