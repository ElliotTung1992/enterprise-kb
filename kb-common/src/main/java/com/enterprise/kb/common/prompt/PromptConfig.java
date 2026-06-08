package com.enterprise.kb.common.prompt;

import com.langfuse.client.LangfuseClient;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

/**
 * Prompt 管理装配。
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PromptProperties.class)
public class PromptConfig {

    private static final List<String> PRELOAD_PROMPTS = List.of(
            "kb/agentic/system",
            "kb/router/domain",
            "kb/customer/assistant",
            "kb/complaint/responsibility",
            "kb/complaint/handler",
            "kb/complaint/executor",
            "kb/aftersales/handler",
            "kb/profile/preference-inference",
            "kb/image/understanding");

    @Bean
    public MustacheLite mustacheLite() {
        return new MustacheLite();
    }

    @Bean
    public LocalPromptStore localPromptStore() {
        return new LocalPromptStore();
    }

    @Bean
    @ConditionalOnProperty(prefix = "enterprise.kb.prompt", name = "enabled", havingValue = "false", matchIfMissing = true)
    public PromptProvider localPromptProvider(LocalPromptStore localPromptStore, MustacheLite mustacheLite) {
        return new LocalPromptProvider(localPromptStore, mustacheLite);
    }

    @Bean
    @ConditionalOnProperty(prefix = "enterprise.kb.prompt", name = "enabled", havingValue = "true")
    public LangfuseClient langfuseClient(PromptProperties properties) {
        return LangfuseClient.builder()
                .url(properties.getBaseUrl())
                .credentials(properties.getPublicKey(), properties.getSecretKey())
                .timeout(Math.max(1, (int) properties.getClient().getTimeout().toSeconds()))
                .maxRetries(properties.getClient().getMaxRetries())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "enterprise.kb.prompt", name = "enabled", havingValue = "true")
    public LangfusePromptClient langfusePromptClient(LangfuseClient langfuseClient, PromptProperties properties) {
        return new LangfusePromptClient(langfuseClient, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "enterprise.kb.prompt", name = "enabled", havingValue = "true")
    public LangfusePromptProvider langfusePromptProvider(LangfusePromptClient langfusePromptClient,
                                                         LocalPromptStore localPromptStore,
                                                         MustacheLite mustacheLite,
                                                         PromptProperties properties,
                                                         ObjectProvider<MeterRegistry> meterRegistry) {
        return new LangfusePromptProvider(langfusePromptClient, localPromptStore, mustacheLite,
                properties, Optional.ofNullable(meterRegistry.getIfAvailable()));
    }

    @Bean
    @ConditionalOnProperty(prefix = "enterprise.kb.prompt", name = "enabled", havingValue = "true")
    public ApplicationRunner promptPreloader(LangfusePromptProvider promptProvider) {
        return args -> PRELOAD_PROMPTS.forEach(name -> {
            try {
                promptProvider.preload(name);
            } catch (RuntimeException e) {
                log.warn("Prompt 预热失败：name={}", name, e);
            }
        });
    }
}
