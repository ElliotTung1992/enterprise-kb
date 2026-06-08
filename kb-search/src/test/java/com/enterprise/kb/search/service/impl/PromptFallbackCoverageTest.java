package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.TestPromptProviders;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 设计范围内搜索侧 fallback prompt 文件覆盖。
 */
class PromptFallbackCoverageTest {

    @Test
    void searchPromptsRenderFromClasspathFallback() {
        var provider = TestPromptProviders.local();

        assertThat(provider.render("kb/agentic/system",
                Map.of("image_context_rule", "图片规则"))).contains("图片规则");
        assertThat(provider.render("kb/router/domain", Map.of())).contains("意图路由器");
        assertThat(provider.render("kb/customer/assistant", Map.of())).contains("智能客服助手");
        assertThat(provider.render("kb/complaint/responsibility", Map.of())).contains("责任认定助手");
        assertThat(provider.render("kb/complaint/handler", Map.of())).contains("投诉升级专员助手");
        assertThat(provider.render("kb/complaint/executor", Map.of())).contains("投诉处理执行专员");
        assertThat(provider.render("kb/aftersales/handler", Map.of())).contains("售后助手");
        assertThat(provider.render("kb/profile/preference-inference", Map.of())).contains("回答偏好画像");
    }
}
