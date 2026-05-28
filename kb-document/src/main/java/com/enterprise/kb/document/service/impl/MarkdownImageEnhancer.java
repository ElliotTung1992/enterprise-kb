package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.document.model.MdDocumentAsset;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MdImageInput;
import com.enterprise.kb.document.service.MdImageReference;
import com.enterprise.kb.document.service.MdImageUnderstandingResult;
import com.enterprise.kb.document.service.MdImageUnderstandingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 图片增强模块。
 */
@Component
class MarkdownImageEnhancer {

    private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*!\\[([^]]*)]\\((\\S+)(?:\\s+\"([^\"]*)\")?\\)\\s*$");

    private MdImageUrlResolver imageUrlResolver;
    private MdImageUnderstandingService imageUnderstandingService;
    private DocumentObjectStorageService objectStorageService;

    MarkdownImageEnhancer() {
    }

    @Autowired
    MarkdownImageEnhancer(MdImageUrlResolver imageUrlResolver,
                          MdImageUnderstandingService imageUnderstandingService,
                          DocumentObjectStorageService objectStorageService) {
        this.imageUrlResolver = imageUrlResolver;
        this.imageUnderstandingService = imageUnderstandingService;
        this.objectStorageService = objectStorageService;
    }

    EnhancedSection enhance(UUID documentId, UUID spaceId, MarkdownSectionSplitter.SectionSlice section,
                            ParseState state, Config config) {
        List<Line> lines = linesWithOffsets(section.content());
        StringBuilder enhanced = new StringBuilder();
        List<EnhancedImage> images = new ArrayList<>();
        boolean inCodeFence = false;
        for (Line line : lines) {
            String text = line.text();
            String stripped = text.stripLeading();
            boolean codeFence = stripped.startsWith("```");
            if (codeFence) {
                inCodeFence = !inCodeFence;
            }
            Matcher matcher = IMAGE_LINE.matcher(text);
            if (!inCodeFence && matcher.matches()) {
                ensureImageServices();
                if (state.assetIndex >= config.maxImageCount()) {
                    throw new InvalidRequestException("Markdown 图片数量超过限制: " + config.maxImageCount());
                }
                String altText = matcher.group(1).strip();
                String imageUrl = matcher.group(2).strip();
                String title = matcher.group(3) == null ? "" : matcher.group(3).strip();
                MdDocumentAsset asset = buildAsset(documentId, spaceId, section.section(),
                        state.assetIndex++, imageUrl, altText, title, state, config);
                int imageStart = enhanced.length();
                appendLine(enhanced, line);
                enhanced.append('\n');
                enhanced.append(buildImageChunkText(asset)).append('\n');
                images.add(new EnhancedImage(imageStart, enhanced.length(), asset));
                continue;
            }
            appendLine(enhanced, line);
        }
        return new EnhancedSection(enhanced.toString().stripTrailing(), images);
    }

    private MdDocumentAsset buildAsset(UUID documentId, UUID spaceId, String section, int assetIndex,
                                       String imageUrl, String altText, String title, ParseState state,
                                       Config config) {
        MdImageReference reference = imageUrlResolver.resolve(imageUrl);
        ImageUnderstandingBundle bundle = state.understandingCache.computeIfAbsent(reference.objectKey(),
                objectKey -> understandImage(reference, section, altText, title, config));

        MdDocumentAsset asset = new MdDocumentAsset();
        asset.setId(UUID.randomUUID());
        asset.setDocumentId(documentId);
        asset.setSpaceId(spaceId);
        asset.setAssetIndex(assetIndex);
        asset.setImageUrl(reference.imageUrl());
        asset.setObjectKey(reference.objectKey());
        asset.setMimeType(bundle.mimeType());
        asset.setFileSize(bundle.fileSize());
        asset.setSection(section);
        asset.setAltText(altText);
        asset.setTitle(firstText(title, altText, reference.objectKey()));
        asset.setOcrText(bundle.result().ocrText());
        asset.setCaption(bundle.result().caption());
        asset.setSummary(bundle.result().summary());
        asset.setEntities(bundle.result().entities());
        asset.setCreatedAt(Instant.now());
        asset.setUpdatedAt(Instant.now());
        return asset;
    }

    private ImageUnderstandingBundle understandImage(MdImageReference reference, String section,
                                                     String altText, String title, Config config) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("kb-md-image-", ".img");
            objectStorageService.downloadFile(reference.objectKey(), tempFile);
            long fileSize = Files.size(tempFile);
            long maxBytes = config.maxImageSizeMb() * 1024 * 1024;
            if (fileSize > maxBytes) {
                throw new InvalidRequestException("Markdown 图片超过最大限制: " + config.maxImageSizeMb() + " MB");
            }
            String mimeType = detectImageMimeType(tempFile, reference.objectKey());
            if (!config.allowedImageMimeTypes().contains(mimeType)) {
                throw new InvalidRequestException("Markdown 图片类型不支持: " + mimeType);
            }
            byte[] bytes = Files.readAllBytes(tempFile);
            MdImageUnderstandingResult result = imageUnderstandingService.understand(new MdImageInput(
                    bytes, mimeType, reference.imageUrl(), reference.objectKey(), section, altText, title));
            if (!StringUtils.hasText(result.caption()) || !StringUtils.hasText(result.summary())) {
                throw new InvalidRequestException("Markdown 图片理解结果缺少 caption 或 summary");
            }
            return new ImageUnderstandingBundle(mimeType, fileSize, result);
        } catch (IOException e) {
            throw new InvalidRequestException("读取 Markdown 图片失败: " + reference.objectKey());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // 临时文件删除失败不影响导入结果。
                }
            }
        }
    }

    private String detectImageMimeType(Path path, String objectKey) throws IOException {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        String detected = Files.probeContentType(path);
        if (StringUtils.hasText(detected)) {
            return normalizeJpegMimeType(detected);
        }
        return "application/octet-stream";
    }

    private String normalizeJpegMimeType(String mimeType) {
        return "image/jpg".equalsIgnoreCase(mimeType) ? "image/jpeg" : mimeType.toLowerCase();
    }

    private String buildImageChunkText(MdDocumentAsset asset) {
        return """
                [图片说明]
                图片标题：%s
                所在章节：%s
                图片地址：%s
                替代文本：%s
                OCR文字：%s
                图片描述：%s
                检索摘要：%s
                关键实体：%s
                [/图片说明]
                """.formatted(
                firstText(asset.getTitle(), asset.getAltText(), asset.getObjectKey()),
                firstText(asset.getSection(), "未命名章节"),
                firstText(asset.getObjectKey(), ""),
                firstText(asset.getAltText(), ""),
                firstText(asset.getOcrText(), ""),
                firstText(asset.getCaption(), ""),
                firstText(asset.getSummary(), ""),
                firstText(asset.getEntities(), "")).strip();
    }

    private void appendLine(StringBuilder builder, Line line) {
        builder.append(line.text());
        if (line.end() > line.start() && line.end() > line.start() + line.text().length()) {
            builder.append('\n');
        }
    }

    private void ensureImageServices() {
        if (imageUrlResolver == null || imageUnderstandingService == null || objectStorageService == null) {
            throw new InvalidRequestException("Markdown 图片处理服务未配置");
        }
    }

    static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private List<Line> linesWithOffsets(String content) {
        List<Line> lines = new ArrayList<>();
        int start = 0;
        while (start < content.length()) {
            int newline = content.indexOf('\n', start);
            int end = newline >= 0 ? newline + 1 : content.length();
            String text = content.substring(start, newline >= 0 ? newline : end);
            lines.add(new Line(text, start, end));
            start = end;
        }
        return lines;
    }

    record Config(int maxImageCount, long maxImageSizeMb, List<String> allowedImageMimeTypes) {}

    /**
     * 增强后的 parent section。
     *
     * @param enhancedContent enhancedContent：增强后的 Markdown 内容
     * @param images          图片资产及其位置
     */
    record EnhancedSection(String enhancedContent, List<EnhancedImage> images) {}

    /**
     * 增强内容中的图片块位置。
     *
     * @param start 图片块在增强 parent content 中的起始位置
     * @param end   图片块在增强 parent content 中的结束位置
     * @param asset 图片资产
     */
    record EnhancedImage(int start, int end, MdDocumentAsset asset) {}

    /**
     * 同一篇 Markdown 导入过程中的图片解析状态。
     */
    static class ParseState {
        private int assetIndex;
        private final Map<String, ImageUnderstandingBundle> understandingCache = new HashMap<>();
    }

    private record ImageUnderstandingBundle(String mimeType, long fileSize, MdImageUnderstandingResult result) {}

    private record Line(String text, int start, int end) {}
}
