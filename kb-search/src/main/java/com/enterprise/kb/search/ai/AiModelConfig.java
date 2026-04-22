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
 * Declares named ChatClient and EmbeddingModel beans for each AI provider.
 * Uses @ConditionalOnProperty so beans are only created when API keys are configured.
 */
@Configuration
public class AiModelConfig {

    // ---- MiniMax (default provider) ----

    @Bean("minimaxChatClient")
    @Primary
    @ConditionalOnProperty("spring.ai.minimax.api-key")
    public ChatClient minimaxChatClient(
            @Qualifier("miniMaxChatModel") ChatModel miniMaxChatModel) {
        return ChatClient.builder(miniMaxChatModel).build();
    }

    @Bean("minimaxEmbeddingModel")
    @Primary
    @ConditionalOnProperty("spring.ai.minimax.api-key")
    public EmbeddingModel minimaxEmbeddingModel(
            @Qualifier("miniMaxEmbeddingModel") EmbeddingModel miniMaxEmbeddingModel) {
        return miniMaxEmbeddingModel;
    }

}
