package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.prompt.LocalPromptProvider;
import com.enterprise.kb.common.prompt.LocalPromptStore;
import com.enterprise.kb.common.prompt.MustacheLite;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 图片理解 fallback prompt 文件覆盖。
 */
class ImagePromptFallbackCoverageTest {

    @Test
    void imageUnderstandingPromptRendersFromClasspathFallback() {
        var provider = new LocalPromptProvider(new LocalPromptStore(), new MustacheLite());

        String prompt = provider.render("kb/image/understanding", Map.of(
                "section", "支付说明",
                "title", "流程图",
                "alt_text", "支付流程",
                "object_key", "images/pay.png"));

        assertThat(prompt).contains("支付说明", "流程图", "支付流程", "images/pay.png");
    }
}
