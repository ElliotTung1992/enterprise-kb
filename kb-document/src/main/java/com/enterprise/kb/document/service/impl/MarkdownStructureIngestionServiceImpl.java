package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;
import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdDocumentAsset;
import com.enterprise.kb.document.model.MdParentChunk;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MarkdownStructureIngestionService;
import com.enterprise.kb.document.service.MdImageUnderstandingService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Markdown 结构感知解析服务实现。
 */
@Service
public class MarkdownStructureIngestionServiceImpl implements MarkdownStructureIngestionService {

    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_MIN_TOKENS = 64;

    private final MarkdownSectionSplitter sectionSplitter;
    private final MarkdownChildPacker childPacker;
    private final MdVectorDocumentFactory vectorDocumentFactory;
    private final MarkdownImageEnhancer imageEnhancer;

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
        this.imageEnhancer = new MarkdownImageEnhancer();
    }

    @Autowired
    public MarkdownStructureIngestionServiceImpl(MarkdownSectionSplitter sectionSplitter,
                                                 MarkdownChildPacker childPacker,
                                                 MdVectorDocumentFactory vectorDocumentFactory,
                                                 MarkdownImageEnhancer imageEnhancer) {
        this.sectionSplitter = sectionSplitter;
        this.childPacker = childPacker;
        this.vectorDocumentFactory = vectorDocumentFactory;
        this.imageEnhancer = imageEnhancer;
    }

    MarkdownStructureIngestionServiceImpl(MdImageUrlResolver imageUrlResolver,
                                          MdImageUnderstandingService imageUnderstandingService,
                                          DocumentObjectStorageService objectStorageService) {
        this.sectionSplitter = new MarkdownSectionSplitter();
        this.childPacker = new MarkdownChildPacker();
        this.vectorDocumentFactory = new MdVectorDocumentFactory();
        this.imageEnhancer = new MarkdownImageEnhancer(imageUrlResolver, imageUnderstandingService, objectStorageService);
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
        MarkdownImageEnhancer.ParseState imageState = new MarkdownImageEnhancer.ParseState();

        for (int i = 0; i < sections.size(); i++) {
            MarkdownSectionSplitter.SectionSlice section = sections.get(i);
            MarkdownImageEnhancer.EnhancedSection enhanced = imageEnhancer.enhance(
                    documentId, spaceId, section, imageState, imageConfig());
            MarkdownSectionSplitter.SectionSlice enhancedSection = new MarkdownSectionSplitter.SectionSlice(
                    section.section(), section.headingLevel(), enhanced.enhancedContent(),
                    section.charStart(), section.charEnd());
            MdParentChunk parent = toParent(documentId, spaceId, enhancedSection, i);
            List<MdChildChunk> childChunks = splitChildren(parent, documentId, spaceId, enhanced.images());
            parent.setChildCount(childChunks.size());
            parents.add(parent);
            children.addAll(childChunks);
            assets.addAll(enhanced.images().stream().map(MarkdownImageEnhancer.EnhancedImage::asset).toList());
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
                                             List<MarkdownImageEnhancer.EnhancedImage> images) {
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
                child.setAssetTitle(MarkdownImageEnhancer.firstText(
                        asset.getTitle(), asset.getAltText(), asset.getObjectKey()));
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

    private MarkdownImageEnhancer.Config imageConfig() {
        return new MarkdownImageEnhancer.Config(maxImageCount, maxImageSizeMb, allowedImageMimeTypes);
    }
}
