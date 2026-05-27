package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.MdImageInput;
import com.enterprise.kb.document.service.MdImageUnderstandingResult;
import com.enterprise.kb.document.service.MdImageUnderstandingService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Markdown 图片理解占位实现，仅在显式配置 NOOP 时启用。
 */
@Service
@ConditionalOnProperty(name = "enterprise.kb.md.image.vision-provider", havingValue = "NOOP")
public class NoopMdImageUnderstandingService implements MdImageUnderstandingService {

    @Override
    public MdImageUnderstandingResult understand(MdImageInput image) {
        String title = StringUtils.hasText(image.title()) ? image.title() : image.altText();
        if (!StringUtils.hasText(title)) {
            title = image.objectKey();
        }
        String summary = "图片位于《%s》章节，标题为「%s」，对象路径为 %s。"
                .formatted(firstText(image.section(), "未命名章节"), title, image.objectKey());
        return new MdImageUnderstandingResult("", summary, summary, title);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}

