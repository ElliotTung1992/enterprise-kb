package com.enterprise.kb.common.prompt;

import com.langfuse.client.LangfuseClient;
import com.langfuse.client.core.RequestOptions;
import com.langfuse.client.resources.prompts.requests.GetPromptRequest;
import com.langfuse.client.resources.prompts.types.Prompt;
import com.langfuse.client.resources.prompts.types.TextPrompt;

import java.util.concurrent.TimeUnit;

/**
 * LangFuse prompt API 薄客户端。
 */
public class LangfusePromptClient {

    private final LangfuseClient client;
    private final PromptProperties properties;

    public LangfusePromptClient(LangfuseClient client, PromptProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 从 LangFuse 拉取 text prompt。
     *
     * @param name prompt 名称
     * @return 缓存模板
     */
    public CachedPrompt fetch(String name) {
        GetPromptRequest request = GetPromptRequest.builder()
                .label(properties.getLabel())
                .build();
        RequestOptions options = RequestOptions.builder()
                .timeout((int) properties.getClient().getTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .build();
        Prompt prompt = client.prompts().get(name, request, options);
        TextPrompt textPrompt = prompt.getText()
                .orElseThrow(() -> new PromptRenderException("LangFuse prompt 不是 text 类型: " + name));
        return new CachedPrompt(textPrompt.getName(), textPrompt.getVersion(), textPrompt.getPrompt());
    }
}
