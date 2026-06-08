package com.enterprise.kb.search;

import com.enterprise.kb.common.prompt.LocalPromptProvider;
import com.enterprise.kb.common.prompt.LocalPromptStore;
import com.enterprise.kb.common.prompt.MustacheLite;
import com.enterprise.kb.common.prompt.PromptProvider;

/**
 * 测试用 PromptProvider 工厂。
 */
public final class TestPromptProviders {

    private TestPromptProviders() {
    }

    public static PromptProvider local() {
        return new LocalPromptProvider(new LocalPromptStore(), new MustacheLite());
    }
}
