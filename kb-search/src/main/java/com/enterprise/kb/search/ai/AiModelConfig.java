package com.enterprise.kb.search.ai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;


/**
 * AI 模型提供商的 Bean 声明配置。
 *
 * <p>系统支持多个 AI 提供商。本配置类为每个提供商声明独立命名的 {@link ChatClient}
 * 和 {@link EmbeddingModel} Bean，供 {@link com.enterprise.kb.search.ai.ModelProviderResolver}
 * 在运行时按请求动态选择。
 *
 * <p><b>条件装配规则：</b>使用 {@link ConditionalOnProperty} 注解，仅当对应 API Key
 * 的配置项存在时才创建 Bean，避免因未配置的提供商导致启动失败。
 *
 * <p><b>默认提供商：</b>通过 {@code enterprise.kb.ai.default-provider} 配置。
 *
 * <p><b>Bean 命名约定：</b>
 * <ul>
 *   <li>ChatClient：{@code dashscopeChatClient}、{@code llamaCppChatClient}</li>
 *   <li>EmbeddingModel：{@code dashscopeEmbeddingModel}</li>
 * </ul>
 *
 * <p><b>向量维度：</b>当前 Milvus collection 使用 1536 维。若切换 Embedding 提供商，
 * 需确保维度一致，否则须重建 collection 并重新向量化所有文档。
 */
@Configuration
public class AiModelConfig {

    @Value("${enterprise.kb.ai.llama-cpp.base-url:http://localhost:8079}")
    private String llamaCppBaseUrl;

    @Value("${enterprise.kb.ai.llama-cpp.api-key:local}")
    private String llamaCppApiKey;

    @Value("${enterprise.kb.ai.llama-cpp.model:qwen3-vl-8b-instruct}")
    private String llamaCppModel;

    // ---- DashScope（Spring AI Alibaba 原生接口） ----

    /**
     * 创建 DashScope 的 {@link ChatClient}（通义千问），用于问答生成。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.dashscope.api-key} 时生效。
     * 标注 {@link Primary} 作为存在多个 {@link ChatModel} 时的默认候选。
     *
     * @param dashScopeChatModel Spring AI Alibaba 自动装配的 DashScope ChatModel
     * @return 封装了 DashScope 模型的 {@link ChatClient}
     */
    @Bean("dashscopeChatClient")
    @Primary
    @ConditionalOnProperty("spring.ai.dashscope.api-key")
    public ChatClient dashscopeChatClient(
            @Qualifier("dashScopeChatModel") ChatModel dashScopeChatModel) {
        return ChatClient.builder(dashScopeChatModel).build();
    }

    // ---- llama.cpp 本地模型（OpenAI 兼容接口） ----

    /**
     * 创建 llama.cpp 本地大模型的 {@link ChatClient}。
     *
     * <p>llama.cpp server 暴露 OpenAI 兼容接口时，本项目可通过
     * {@code enterprise.kb.ai.llama-cpp.base-url} 直接接入本地模型。
     *
     * @param observationRegistry 容器内的可观测注册表，用于产出 LLM span
     * @return 封装了 llama.cpp 本地模型的 {@link ChatClient}
     */
    @Bean("llamaCppChatClient")
    @ConditionalOnProperty(name = "enterprise.kb.ai.llama-cpp.enabled", havingValue = "true", matchIfMissing = true)
    public ChatClient llamaCppChatClient(ObservationRegistry observationRegistry) {
        return openAiCompatibleChatClient(
                llamaCppBaseUrl,
                llamaCppApiKey,
                llamaCppModel,
                observationRegistry);
    }

    private ChatClient openAiCompatibleChatClient(
            String baseUrl,
            String apiKey,
            String model,
            ObservationRegistry observationRegistry) {
        // 1. 定义一个拦截器，专门负责清洗 <think> 标签
        org.springframework.http.client.ClientHttpRequestInterceptor cleanThinkInterceptor =
                (request, body, execution) -> {
                    org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);

                    // 读取原始返回体
                    java.io.InputStream bodyStream = response.getBody();
                    String responseStr = org.springframework.util.StreamUtils.copyToString(bodyStream, java.nio.charset.StandardCharsets.UTF_8);

                    // 核心逻辑：用正则表达式抹除 <think>...</think> 及其内部的所有内容
                    // 使用 (?s) 确保可以跨越所有的转义换行符
                    String cleanedStr = responseStr.replaceAll("(?s)<think>.*?</think>", "");

                    byte[] cleanedBytes = cleanedStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                    // 将清洗后的干净 JSON 重新包装回去
                    return new org.springframework.http.client.ClientHttpResponse() {
                        @Override @org.springframework.lang.NonNull
                        public java.io.InputStream getBody() { return new java.io.ByteArrayInputStream(cleanedBytes); }
                        @Override @org.springframework.lang.NonNull
                        public org.springframework.http.HttpHeaders getHeaders() { return response.getHeaders(); }
                        @Override @org.springframework.lang.NonNull
                        public org.springframework.http.HttpStatusCode getStatusCode() throws java.io.IOException { return response.getStatusCode(); }
                        @Override @org.springframework.lang.NonNull
                        public String getStatusText() throws java.io.IOException { return response.getStatusText(); }
                        @Override public void close() { response.close(); }
                    };
                };

        // 2. 将拦截器注入到 RestClient 中
        org.springframework.web.client.RestClient.Builder restClientBuilder =
                org.springframework.web.client.RestClient.builder()
                        .requestInterceptor(cleanThinkInterceptor);

        // 3. 构建 OpenAiApi 时使用自定义的 RestClient
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        // 4. 注入 ObservationRegistry，使手工模型产出 gen_ai.* LLM span
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .observationRegistry(observationRegistry)
                .build();

        // ChatClient 层同样带上 registry（conventions 传 null 走默认），使 ChatClient span 生效
        return ChatClient.builder(chatModel, observationRegistry, null, null).build();
    }

    /**
     * BeanFactoryPostProcessor：在 bean 实例化之前将 {@code dashscopeEmbeddingModel} 设为 primary，
     * 解决 MilvusVectorStore 面对多个 EmbeddingModel 候选时的注入歧义。
     *
     * <p>存在多个 EmbeddingModel 候选时，Milvus 按类型注入可能产生歧义。直接设置
     * {@code dashscopeEmbeddingModel.primary = true} 后 Spring 会优先选它。
     * 其他 EmbeddingModel bean 仍可通过 {@code @Qualifier} 按名称注入，供 ModelProviderResolver 使用。
     */
    @Bean
    public static BeanFactoryPostProcessor embeddingModelPrimaryPostProcessor() {
        return beanFactory -> {
            if (!(beanFactory instanceof DefaultListableBeanFactory dlbf)) return;
            if (dlbf.containsBeanDefinition("dashscopeEmbeddingModel")) {
                dlbf.getBeanDefinition("dashscopeEmbeddingModel").setPrimary(true);
            }
        };
    }

}
