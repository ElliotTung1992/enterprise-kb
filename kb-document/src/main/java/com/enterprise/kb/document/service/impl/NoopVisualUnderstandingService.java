package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.service.VisualUnderstandingResult;
import com.enterprise.kb.document.service.VisualUnderstandingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 默认视觉理解实现。
 * <p>不调用外部模型，仅基于 Markdown 元数据生成稳定的占位 caption/summary，便于先打通异步处理链路。</p>
 */
@Service
public class NoopVisualUnderstandingService implements VisualUnderstandingService {

    @Override
    public VisualUnderstandingResult understand(DocumentAsset asset) {
        if (asset.getAssetType() == AssetType.IMAGE) {
            String title = firstText(asset.getTitle(), asset.getAltText(), asset.getOriginalPath(), "未命名图片");
            String summary = "图片位于《%s》章节，标题为「%s」，原始路径为 %s。"
                    .formatted(firstText(asset.getSection(), "未命名章节"), title, firstText(asset.getOriginalPath(), ""));
            return new VisualUnderstandingResult("", summary, summary, title);
        }
        String diagramType = asset.getDiagramType() == null ? "流程图" : asset.getDiagramType().name();
        String caption = "%s 位于《%s》章节，标题为「%s」。"
                .formatted(diagramType, firstText(asset.getSection(), "未命名章节"), firstText(asset.getTitle(), "未命名流程图"));
        String sourceSummary = StringUtils.hasText(asset.getSourceCode())
                ? asset.getSourceCode().replaceAll("\\s+", " ").strip()
                : caption;
        return new VisualUnderstandingResult("", caption, sourceSummary, firstText(asset.getTitle(), asset.getSection(), ""));
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }
}
