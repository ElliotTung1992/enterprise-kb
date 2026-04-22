package com.enterprise.kb.search.ai;

import com.enterprise.kb.common.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Runtime resolver for multi-model switching.
 * Priority: request param > space preference > system default.
 */
@Slf4j
@Component
public class ModelProviderResolver {

    private final Map<String, ChatClient> chatClients = new HashMap<>();
    private final Map<String, EmbeddingModel> embeddingModels = new HashMap<>();

    @Value("${enterprise.kb.ai.default-provider:MINIMAX}")
    private String defaultChatProvider;

    @Value("${enterprise.kb.ai.default-embedding-provider:MINIMAX}")
    private String defaultEmbeddingProvider;

    public ModelProviderResolver(
            @Nullable @Qualifier("minimaxChatClient") ChatClient minimax,
            @Nullable @Qualifier("minimaxEmbeddingModel") EmbeddingModel minimaxEmbedding) {

        if (minimax != null) chatClients.put("MINIMAX", minimax);
        if (minimaxEmbedding != null) embeddingModels.put("MINIMAX", minimaxEmbedding);
    }

    public ChatClient resolveChatClient(@Nullable String provider) {
        String key = normalize(provider, defaultChatProvider);
        ChatClient client = chatClients.get(key);
        if (client == null) {
            log.warn("No ChatClient found for provider '{}', falling back to default", key);
            client = chatClients.values().stream().findFirst()
                    .orElseThrow(() -> new InvalidRequestException("No AI chat provider configured"));
        }
        return client;
    }

    public EmbeddingModel resolveEmbeddingModel(@Nullable String provider) {
        String key = normalize(provider, defaultEmbeddingProvider);
        EmbeddingModel model = embeddingModels.get(key);
        if (model == null) {
            log.warn("No EmbeddingModel found for provider '{}', falling back to default", key);
            model = embeddingModels.values().stream().findFirst()
                    .orElseThrow(() -> new InvalidRequestException("No AI embedding provider configured"));
        }
        return model;
    }

    private String normalize(String provider, String fallback) {
        return (provider != null && !provider.isBlank()) ? provider.toUpperCase() : fallback.toUpperCase();
    }
}
