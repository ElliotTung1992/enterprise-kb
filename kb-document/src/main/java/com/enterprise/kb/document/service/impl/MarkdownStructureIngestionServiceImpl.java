package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;
import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdDocumentAsset;
import com.enterprise.kb.document.model.MdParentChunk;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MarkdownStructureIngestionService;
import com.enterprise.kb.document.service.MdImageInput;
import com.enterprise.kb.document.service.MdImageReference;
import com.enterprise.kb.document.service.MdImageUnderstandingResult;
import com.enterprise.kb.document.service.MdImageUnderstandingService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Markdown 结构感知解析服务实现。
 */
@Service
public class MarkdownStructureIngestionServiceImpl implements MarkdownStructureIngestionService {

    private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*!\\[([^]]*)]\\((\\S+)(?:\\s+\"([^\"]*)\")?\\)\\s*$");
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_MIN_TOKENS = 64;

    private final MarkdownSectionSplitter sectionSplitter;
    private final MarkdownChildPacker childPacker;
    private final MdVectorDocumentFactory vectorDocumentFactory;
    private MdImageUrlResolver imageUrlResolver;
    private MdImageUnderstandingService imageUnderstandingService;
    private DocumentObjectStorageService objectStorageService;

    @Value("${enterprise.kb.chunking.markdown.max-tokens:512}")
    private int maxTokens = DEFAULT_MAX_TOKENS;

    @Value("${enterprise.kb.chunking.markdown.min-tokens:64}")
    private int minTokens = DEFAULT_MIN_TOKENS;

    /** 切分到第几级标题（默认 H1-H3），由配置驱动 parent 切分粒度。 */
    @Value("${enterprise.kb.chunking.markdown.split-heading-levels:3}")
    private int splitHeadingLevels = 3;

    @Value("${enterprise.kb.md.image.max-count:50}")
    private int maxImageCount = 50;

    @Value("${enterprise.kb.md.image.max-size-mb:10}")
    private long maxImageSizeMb = 10;

    @Value("${enterprise.kb.md.image.allowed-mime-types:image/png,image/jpeg,image/webp}")
    private List<String> allowedImageMimeTypes = List.of("image/png", "image/jpeg", "image/webp");

    public MarkdownStructureIngestionServiceImpl() {
        this.sectionSplitter = new MarkdownSectionSplitter();
        this.childPacker = new MarkdownChildPacker();
        this.vectorDocumentFactory = new MdVectorDocumentFactory();
    }

    @Autowired
    public MarkdownStructureIngestionServiceImpl(MdImageUrlResolver imageUrlResolver,
                                                 MdImageUnderstandingService imageUnderstandingService,
                                                 DocumentObjectStorageService objectStorageService,
                                                 MarkdownSectionSplitter sectionSplitter,
                                                 MarkdownChildPacker childPacker,
                                                 MdVectorDocumentFactory vectorDocumentFactory) {
        this.sectionSplitter = sectionSplitter;
        this.childPacker = childPacker;
        this.vectorDocumentFactory = vectorDocumentFactory;
        this.imageUrlResolver = imageUrlResolver;
        this.imageUnderstandingService = imageUnderstandingService;
        this.objectStorageService = objectStorageService;
    }

    MarkdownStructureIngestionServiceImpl(MdImageUrlResolver imageUrlResolver,
                                          MdImageUnderstandingService imageUnderstandingService,
                                          DocumentObjectStorageService objectStorageService) {
        this();
        this.imageUrlResolver = imageUrlResolver;
        this.imageUnderstandingService = imageUnderstandingService;
        this.objectStorageService = objectStorageService;
    }

    /**
     * 按 H1-H3 parent 与段落级 child 解析 Markdown。
     *
     * @param documentId Markdown 文档 ID
     * @param spaceId    空间 ID
     * @param filePath   原始文件路径
     * @return parent、child 与向量文档的解析结果
     */
    @Override
    public MarkdownStructureIngestionResult parse(UUID documentId, UUID spaceId, String filePath) {
        String markdown = readFile(filePath);
        List<MarkdownSectionSplitter.SectionSlice> sections = sectionSplitter.split(markdown, splitHeadingLevels);
        List<MdParentChunk> parents = new ArrayList<>();
        List<MdChildChunk> children = new ArrayList<>();
        List<MdDocumentAsset> assets = new ArrayList<>();
        List<Document> vectorDocuments = new ArrayList<>();
        ImageParseState imageState = new ImageParseState();

        for (int i = 0; i < sections.size(); i++) {
            MarkdownSectionSplitter.SectionSlice section = sections.get(i);
            EnhancedSection enhanced = enhanceImages(documentId, spaceId, section, imageState);
            MarkdownSectionSplitter.SectionSlice enhancedSection = new MarkdownSectionSplitter.SectionSlice(
                    section.section(), section.headingLevel(), enhanced.content(), section.charStart(), section.charEnd());
            MdParentChunk parent = toParent(documentId, spaceId, enhancedSection, i);
            List<MdChildChunk> childChunks = splitChildren(parent, documentId, spaceId, enhanced.images());
            parent.setChildCount(childChunks.size());
            parents.add(parent);
            children.addAll(childChunks);
            assets.addAll(enhanced.images().stream().map(EnhancedImage::asset).toList());
            for (MdChildChunk child : childChunks) {
                vectorDocuments.add(vectorDocumentFactory.from(child));
            }
        }
        return new MarkdownStructureIngestionResult(parents, children, assets, vectorDocuments);
    }

    private String readFile(String filePath) {
        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Markdown 文件失败: " + filePath, e);
        }
    }

    private MdParentChunk toParent(UUID documentId, UUID spaceId, MarkdownSectionSplitter.SectionSlice section,
                                   int ordinal) {
        MdParentChunk parent = new MdParentChunk();
        parent.setId(UUID.randomUUID());
        parent.setDocumentId(documentId);
        parent.setSpaceId(spaceId);
        parent.setSection(section.section());
        parent.setHeadingLevel(section.headingLevel());
        parent.setContent(section.content());
        parent.setOrdinal(ordinal);
        parent.setCharStart(section.charStart());
        parent.setCharEnd(section.charEnd());
        parent.setCreatedAt(Instant.now());
        return parent;
    }

    private List<MdChildChunk> splitChildren(MdParentChunk parent, UUID documentId, UUID spaceId,
                                             List<EnhancedImage> images) {
        List<MarkdownChildPacker.ImageBlock> imageBlocks = images.stream()
                .map(image -> new MarkdownChildPacker.ImageBlock(image.start(), image.end(), image.asset()))
                .toList();
        List<MarkdownChildPacker.ChildSlice> slices = childPacker.pack(
                parent.getContent(), imageBlocks, maxTokens, minTokens);
        List<MdChildChunk> children = new ArrayList<>();
        for (int i = 0; i < slices.size(); i++) {
            MarkdownChildPacker.ChildSlice slice = slices.get(i);
            MdChildChunk child = new MdChildChunk();
            child.setId(UUID.randomUUID());
            child.setParentId(parent.getId());
            child.setDocumentId(documentId);
            child.setSpaceId(spaceId);
            child.setSection(parent.getSection());
            child.setSeqInParent(i);
            child.setEmbedText(slice.embedText());
            child.setContentType(slice.asset() == null ? "TEXT" : "IMAGE_CAPTION");
            if (slice.asset() != null) {
                MdDocumentAsset asset = slice.asset();
                child.setAssetId(asset.getId());
                child.setAssetUrl(asset.getImageUrl());
                child.setAssetTitle(firstText(asset.getTitle(), asset.getAltText(), asset.getObjectKey()));
                child.setAssetObjectKey(asset.getObjectKey());
                asset.setChildChunkId(child.getId());
            }
            child.setTokenCount(MarkdownTokenEstimator.estimate(slice.embedText()));
            child.setCharStart(slice.charStart());
            child.setCharEnd(slice.charEnd());
            child.setCreatedAt(Instant.now());
            children.add(child);
        }
        return children;
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

    private EnhancedSection enhanceImages(UUID documentId, UUID spaceId, MarkdownSectionSplitter.SectionSlice section,
                                          ImageParseState state) {
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
                if (state.assetIndex >= maxImageCount) {
                    throw new InvalidRequestException("Markdown 图片数量超过限制: " + maxImageCount);
                }
                String altText = matcher.group(1).strip();
                String imageUrl = matcher.group(2).strip();
                String title = matcher.group(3) == null ? "" : matcher.group(3).strip();
                MdDocumentAsset asset = buildAsset(documentId, spaceId, section.section(),
                        state.assetIndex++, imageUrl, altText, title, state);
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
                                       String imageUrl, String altText, String title, ImageParseState state) {
        MdImageReference reference = imageUrlResolver.resolve(imageUrl);
        ImageUnderstandingBundle bundle = state.understandingCache.computeIfAbsent(reference.objectKey(),
                objectKey -> understandImage(reference, section, altText, title));

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
                                                     String altText, String title) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("kb-md-image-", ".img");
            objectStorageService.downloadFile(reference.objectKey(), tempFile);
            long fileSize = Files.size(tempFile);
            long maxBytes = maxImageSizeMb * 1024 * 1024;
            if (fileSize > maxBytes) {
                throw new InvalidRequestException("Markdown 图片超过最大限制: " + maxImageSizeMb + " MB");
            }
            String mimeType = detectImageMimeType(tempFile, reference.objectKey());
            if (!allowedImageMimeTypes.contains(mimeType)) {
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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 增强后的 parent section。
     *
     * @param content 增强后的 Markdown 内容
     * @param images  图片资产及其位置
     */
    private record EnhancedSection(String content, List<EnhancedImage> images) {}

    /**
     * 增强内容中的图片块位置。
     *
     * @param start 图片块在增强 parent content 中的起始位置
     * @param end   图片块在增强 parent content 中的结束位置
     * @param asset 图片资产
     */
    private record EnhancedImage(int start, int end, MdDocumentAsset asset) {}

    /**
     * 同一篇 Markdown 导入过程中的图片解析状态。
     */
    private static class ImageParseState {
        private int assetIndex;
        private final Map<String, ImageUnderstandingBundle> understandingCache = new HashMap<>();
    }

    /**
     * 可复用的图片理解结果。
     *
     * @param mimeType 图片 MIME 类型
     * @param fileSize 图片大小
     * @param result   理解结果
     */
    private record ImageUnderstandingBundle(String mimeType, long fileSize, MdImageUnderstandingResult result) {}

    /**
     * 带字符位置的 Markdown 单行。
     *
     * @param text  行文本，不包含换行符
     * @param start 行在当前内容中的起始字符下标
     * @param end   行在当前内容中的结束字符下标，包含换行符位置
     */
    private record Line(String text, int start, int end) {}

}
