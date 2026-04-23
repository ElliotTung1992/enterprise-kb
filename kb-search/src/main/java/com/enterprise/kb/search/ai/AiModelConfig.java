package com.enterprise.kb.search.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
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
 *   <li>ChatClient：{@code minimaxChatClient}、{@code openaiChatClient}、
 *       {@code anthropicChatClient}</li>
 *   <li>EmbeddingModel：{@code minimaxEmbeddingModel}、{@code openaiEmbeddingModel}</li>
 * </ul>
 *
 * <p><b>向量维度：</b>当前统一使用 1536 维（兼容 MiniMax embo-01 和 OpenAI
 * text-embedding-3-small）。如需切换提供商，需确保 Milvus collection 维度一致，
 * 否则须重建 collection 并重新向量化所有文档。
 */
@Configuration
public class AiModelConfig {

    // ---- DashScope（默认提供商，通过 OpenAI 兼容接口） ----

    /**
     * 创建 DashScope 的 {@link ChatClient}（通义千问），用于问答生成。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.openai.api-key} 时生效。
     * 标注 {@link Primary} 使其成为未指定提供商时的默认 ChatClient。
     *
     * @param openAiChatModel Spring AI 自动装配的 OpenAI-compatible ChatModel
     * @return 封装了 DashScope 模型的 {@link ChatClient}
     */
    @Bean("dashscopeChatClient")
    @Primary
    @ConditionalOnProperty("spring.ai.openai.api-key")
    public ChatClient dashscopeChatClient(
            @Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    /**
     * 创建 DashScope 的 {@link EmbeddingModel}（text-embedding-v2，1536 维），用于文档向量化和查询向量化。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.openai.api-key} 时生效。
     * 标注 {@link Primary} 使其成为未指定提供商时的默认嵌入模型。
     *
     * @param openAiEmbeddingModel Spring AI 自动装配的 OpenAI-compatible EmbeddingModel
     * @return DashScope EmbeddingModel 实例
     */
    @Bean("dashscopeEmbeddingModel")
    @Primary
    @ConditionalOnProperty("spring.ai.openai.api-key")
    public EmbeddingModel dashscopeEmbeddingModel(
            @Qualifier("openAiEmbeddingModel") EmbeddingModel openAiEmbeddingModel) {
        return openAiEmbeddingModel;
    }

    // ---- MiniMax ----

    /**
     * 创建 MiniMax 的 {@link ChatClient}，用于问答生成。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.minimax.api-key} 时生效。
     *
     * @param miniMaxChatModel Spring AI 自动装配的 MiniMax ChatModel
     * @return 封装了 MiniMax 模型的 {@link ChatClient}
     */
    @Bean("minimaxChatClient")
    @ConditionalOnProperty("spring.ai.minimax.api-key")
    public ChatClient minimaxChatClient(
            @Qualifier("miniMaxChatModel") ChatModel miniMaxChatModel) {
        return ChatClient.builder(miniMaxChatModel).build();
    }

    /**
     * 创建 MiniMax 的 {@link EmbeddingModel}（embo-01），用于文档向量化和查询向量化。
     *
     * <p>仅当配置文件中存在 {@code spring.ai.minimax.api-key} 时生效。
     *
     * @param miniMaxEmbeddingModel Spring AI 自动装配的 MiniMax EmbeddingModel
     * @return MiniMax EmbeddingModel 实例
     */
    @Bean("minimaxEmbeddingModel")
    @ConditionalOnProperty("spring.ai.minimax.api-key")
    public EmbeddingModel minimaxEmbeddingModel(
            @Qualifier("miniMaxEmbeddingModel") EmbeddingModel miniMaxEmbeddingModel) {
        return miniMaxEmbeddingModel;
    }

}
