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
 * <p>系统支持多个 AI 提供商（MiniMax、OpenAI、Anthropic），通过环境变量中的 API Key
 * 控制哪些提供商可用。本配置类为每个提供商声明独立命名的 {@link ChatClient} 和
 * {@link EmbeddingModel} Bean，供 {@link com.enterprise.kb.search.ai.ModelProviderResolver}
 * 在运行时按用户请求动态选择。
 *
 * <p><b>条件装配规则：</b>使用 {@link ConditionalOnProperty} 注解，仅当对应 API Key
 * 的配置项存在时才创建 Bean，避免因未配置的提供商导致启动失败。
 *
 * <p><b>默认提供商：</b>MiniMax 标注了 {@link Primary}，在未显式指定提供商时作为默认选择。
 *
 * <p><b>Bean 命名约定：</b>
 * <ul>
 *   <li>ChatClient：{@code dashscopeChatClient}、{@code minimaxChatClient}</li>
 *   <li>EmbeddingModel：{@code dashscopeEmbeddingModel}、{@code minimaxEmbeddingModel}</li>
 * </ul>
 *
 * <p><b>向量维度：</b>当前统一使用 1536 维（兼容 MiniMax embo-01 和 OpenAI
 * text-embedding-3-small）。如需切换提供商，需确保 Milvus collection 维度一致，
 * 否则须重建 collection 并重新向量化所有文档。
 */
@Configuration
public class AiModelConfig {

    @Value("${spring.ai.minimax.api-key:}")
    private String minimaxApiKey;

    @Value("${enterprise.kb.ai.minimax-openai-base-url:https://api.minimax.chat/v1}")
    private String minimaxOpenAiBaseUrl;

    // ---- DashScope（默认提供商，Spring AI Alibaba 原生接口） ----

    /**
     * 创建 DashScope 的 {@link ChatClient}（通义千问），用于问答生成。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.dashscope.api-key} 时生效。
     * 标注 {@link Primary} 使其成为未指定提供商时的默认 ChatClient。
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

    // ---- MiniMax ----

    /**
     * 创建 MiniMax 的 {@link ChatClient}，通过 MiniMax 的 OpenAI 兼容接口调用。
     *
     * <p>使用 {@code spring-ai-openai} 适配器而非原生 MiniMax 适配器，
     * 解决原生适配器 tool calling 响应解析异常的问题，确保 ReactAgent 正常运行。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.minimax.api-key} 时生效。
     *
     * <p><b>Tracing（ADR-015 埋点盲区 A）：</b>手工构建的 {@link OpenAiChatModel} 默认落
     * {@code ObservationRegistry.NOOP}，而 MiniMax 是默认 chat provider，会导致默认链路零 trace。
     * 故将容器内的 {@link ObservationRegistry} 同时注入 {@link OpenAiChatModel.Builder} 与
     * {@link ChatClient}，使 MiniMax 调用也能产出 {@code gen_ai.*} LLM span。
     *
     * @param observationRegistry 容器内的可观测注册表，用于产出 LLM span
     * @return 封装了 MiniMax 模型（via OpenAI 兼容接口）的 {@link ChatClient}
     */
    @Bean("minimaxChatClient")
    @ConditionalOnProperty("spring.ai.minimax.api-key")
    public ChatClient minimaxChatClient(ObservationRegistry observationRegistry) {

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
                .baseUrl(minimaxOpenAiBaseUrl)
                .apiKey(minimaxApiKey)
                .restClientBuilder(restClientBuilder)
                .build();

        // 4. 放心地把模型名字换回你套餐支持的 M2.7-highspeed
        //    注入 ObservationRegistry，使该手工模型产出 gen_ai.* LLM span（埋点盲区 A）
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("MiniMax-M2.7-highspeed")
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
     * <p>MiniMax 和 DashScope 自动配置各自注册了 EmbeddingModel，加上 AiModelConfig 手工声明的
     * {@code minimaxEmbeddingModel}，共 3 个候选。Milvus 按类型注入时不知选哪个，直接设置
     * {@code dashscopeEmbeddingModel.primary = true} 后 Spring 会优先选它，不再抛歧义异常。
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
