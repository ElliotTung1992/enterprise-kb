package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.AssetStatus;
import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.common.constants.ChunkContentType;
import com.enterprise.kb.common.constants.DiagramType;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.document.mapper.DocumentAssetMapper;
import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.pipeline.IngestionContext;
import com.enterprise.kb.document.service.DiagramRenderService;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MarkdownVisualIngestionResult;
import com.enterprise.kb.document.service.MarkdownVisualIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Markdown zip 可视化入库实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarkdownVisualIngestionServiceImpl implements MarkdownVisualIngestionService {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^]]*)]\\(([^)]+)\\)");
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final int BUFFER_SIZE = 8192;

    private final DocumentAssetMapper assetMapper;
    private final DocumentObjectStorageService objectStorageService;
    private final DiagramRenderService diagramRenderService;

    @Value("${enterprise.kb.document.markdown-archive.max-file-count:200}")
    private int maxFileCount;

    @Value("${enterprise.kb.document.markdown-archive.max-extracted-size-mb:200}")
    private long maxExtractedSizeMb;

    @Value("${enterprise.kb.document.markdown-archive.max-image-count:100}")
    private int maxImageCount;

    @Value("${enterprise.kb.document.markdown-archive.max-image-size-mb:20}")
    private long maxImageSizeMb;

    @Override
    public boolean supports(IngestionContext ctx) {
        String mimeType = ctx.getMimeType();
        return "application/zip".equals(mimeType) || "application/x-zip-compressed".equals(mimeType);
    }

    @Override
    public MarkdownVisualIngestionResult extract(IngestionContext ctx) {
        Path zipPath = Paths.get(ctx.getFilePath());
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("kb-md-archive-");
            extractZipSafely(zipPath, tempDir);
            Path mainMarkdown = findMainMarkdown(tempDir);

            String sourceZipObjectKey = objectKey(ctx, "source", safeFileName(zipPath.getFileName().toString()));
            objectStorageService.uploadFile(sourceZipObjectKey, zipPath, "application/zip");

            String markdown = Files.readString(mainMarkdown);
            ParsedMarkdown parsed = parseMarkdown(ctx, tempDir, mainMarkdown, markdown);

            log.info("Extracted Markdown visual assets for documentId={}, assets={}, mainMarkdown={}",
                    ctx.getDocumentId(), parsed.assets().size(), tempDir.relativize(mainMarkdown));
            return new MarkdownVisualIngestionResult(
                    parsed.textDocuments(), parsed.assets(), sourceZipObjectKey, tempDir.relativize(mainMarkdown).toString());
        } catch (IOException e) {
            throw new InvalidRequestException("Markdown zip 处理失败: " + e.getMessage());
        } finally {
            if (tempDir != null) deleteDirectoryQuietly(tempDir);
        }
    }

    @Override
    @Transactional
    public void saveAssets(List<DocumentAsset> assets) {
        if (!assets.isEmpty()) {
            assetMapper.insertBatch(assets);
        }
    }

    @Override
    @Transactional
    public void deleteByDocumentId(UUID documentId) {
        List<DocumentAsset> assets = assetMapper.findByDocumentId(documentId);
        for (DocumentAsset asset : assets) {
            deleteObjectQuietly(asset.getObjectKey());
            deleteObjectQuietly(asset.getThumbnailObjectKey());
        }
        assetMapper.deleteByDocumentId(documentId);
    }

    @Override
    public List<Document> buildReferenceChunks(IngestionContext ctx, List<DocumentAsset> assets,
                                               Map<String, Integer> sectionAnchors) {
        List<Document> chunks = new ArrayList<>();
        for (DocumentAsset asset : assets) {
            Integer anchor = sectionAnchors.getOrDefault(normalizeSection(asset.getSection()), 0);
            asset.setAnchorChunkIndex(anchor);

            ChunkContentType contentType = asset.getAssetType() == AssetType.IMAGE
                    ? ChunkContentType.IMAGE_REFERENCE
                    : ChunkContentType.DIAGRAM_SOURCE;
            String text = asset.getAssetType() == AssetType.IMAGE
                    ? imageReferenceText(asset)
                    : diagramSourceText(asset);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("documentId", ctx.getDocumentId().toString());
            metadata.put("spaceId", ctx.getSpaceId().toString());
            metadata.put("contentType", contentType.name());
            metadata.put("assetId", asset.getId().toString());
            metadata.put("section", asset.getSection());
            metadata.put("anchorChunkIndex", anchor);
            metadata.put("assetType", asset.getAssetType().name());
            if (asset.getDiagramType() != null) metadata.put("diagramType", asset.getDiagramType().name());
            chunks.add(new Document(UUID.randomUUID().toString(), text, metadata));
        }
        return chunks;
    }

    private void extractZipSafely(Path zipPath, Path targetDir) throws IOException {
        int fileCount = 0;
        long totalSize = 0;
        long maxExtractedBytes = maxExtractedSizeMb * 1024 * 1024;

        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName();
                if (!StringUtils.hasText(name) || Paths.get(name).isAbsolute()) {
                    throw new InvalidRequestException("zip 包含非法路径: " + name);
                }
                Path output = targetDir.resolve(name).normalize();
                if (!output.startsWith(targetDir)) {
                    throw new InvalidRequestException("zip 包含路径穿越: " + name);
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                    continue;
                }
                fileCount++;
                if (fileCount > maxFileCount) {
                    throw new InvalidRequestException("zip 文件数量超过限制: " + maxFileCount);
                }
                Files.createDirectories(output.getParent());
                long written = copyEntry(zipInputStream, output, maxExtractedBytes - totalSize);
                totalSize += written;
                if (totalSize > maxExtractedBytes) {
                    throw new InvalidRequestException("zip 解压后大小超过限制: " + maxExtractedSizeMb + " MB");
                }
            }
        }
    }

    private long copyEntry(ZipInputStream zipInputStream, Path output, long remainingBytes) throws IOException {
        long written = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        try (var out = Files.newOutputStream(output)) {
            while ((read = zipInputStream.read(buffer)) >= 0) {
                written += read;
                if (written > remainingBytes) {
                    throw new InvalidRequestException("zip 解压后大小超过限制: " + maxExtractedSizeMb + " MB");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }

    private Path findMainMarkdown(Path root) throws IOException {
        List<Path> markdownFiles;
        try (var stream = Files.walk(root)) {
            markdownFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".md") || name.endsWith(".markdown");
                    })
                    .sorted()
                    .toList();
        }
        if (markdownFiles.isEmpty()) {
            throw new InvalidRequestException("zip 中未找到 Markdown 文件");
        }
        if (markdownFiles.size() == 1) {
            return markdownFiles.getFirst();
        }
        return markdownFiles.stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase("README.md"))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException("zip 中包含多个 Markdown 文件，请保留单个主 Markdown 或 README.md"));
    }

    /**
     * 解析 zip 中的主 Markdown 文件，生成正文 Document 和 Phase 1 视觉资产。
     * <p>该方法按标题维护当前 section：普通正文写入文本 Document；图片语法会被改写为 alt/文件名文本，
     * 同时为安全相对路径图片创建 IMAGE 资产并上传原图到 MinIO；Mermaid/PlantUML 代码块会创建 DIAGRAM
     * 资产，渲染器不可用时保留源码并降级为后续的 DIAGRAM_SOURCE chunk。OCR、caption、summary 不在这里执行，
     * 留给 Phase 2 异步视觉理解 worker。</p>
     *
     * @param ctx          文档入库上下文
     * @param root         zip 安全解压后的根目录
     * @param mainMarkdown 主 Markdown 文件路径
     * @param markdown     Markdown 原文
     * @return 正文 Document 与视觉资产列表
     * @throws IOException 读取图片、探测 MIME 或计算 hash 失败时抛出
     */
    private ParsedMarkdown parseMarkdown(IngestionContext ctx, Path root, Path mainMarkdown, String markdown) throws IOException {
        // 用于收集从 Markdown 正文拆出来的 Spring AI 文本 Document。
        List<Document> textDocuments = new ArrayList<>();
        // 用于收集 Markdown 中抽取到的图片和流程图资产。
        List<DocumentAsset> assets = new ArrayList<>();
        // 记录主 Markdown 所在目录，后续用它解析图片相对路径。
        Path markdownDir = mainMarkdown.getParent();
        // 记录主 Markdown 在 zip 解压根目录下的相对路径，作为 source 元数据。
        String source = root.relativize(mainMarkdown).toString();
        // 初始化当前章节名；在遇到第一个标题前，默认使用文件名作为章节名。
        String section = titleFromFile(mainMarkdown);
        // 用于累积当前章节的正文文本，遇到新标题或文档结束时 flush 成 Document。
        StringBuilder sectionText = new StringBuilder();
        // 记录正文 section 序号，写入 Document metadata 便于后续定位。
        int sectionIndex = 0;
        // 记录视觉资产在 Markdown 源文中的出现顺序。
        int assetIndex = 0;
        // 记录已处理的图片数量，用于 enforce 图片数量上限。
        int imageCount = 0;
        // 标记当前是否处于 Markdown fenced code block 内。
        boolean inFence = false;
        // 记录 fenced code block 的语言标识，例如 mermaid、plantuml。
        String fenceLang = "";
        // 用于累积 fenced code block 内部源码。
        StringBuilder fence = new StringBuilder();

        // 按行遍历 Markdown，保留末尾空行，方便识别代码块和章节边界。
        for (String line : markdown.split("\\R", -1)) {
            // 去掉当前行首尾空白，用于判断标题和 fenced code 标记。
            String trimmed = line.strip();
            // 判断当前行是否是 Markdown fenced code block 的开始或结束标记。
            if (trimmed.startsWith("```")) {
                // 如果当前不在代码块中，说明这一行是代码块开始。
                if (!inFence) {
                    // 标记进入 fenced code block。
                    inFence = true;
                    // 提取代码块语言；没有语言标识时使用空字符串。
                    fenceLang = trimmed.length() > 3 ? trimmed.substring(3).strip().toLowerCase(Locale.ROOT) : "";
                    // 初始化代码块内容缓冲区。
                    fence = new StringBuilder();
                    // 开始标记行不进入正文，继续处理下一行。
                    continue;
                }
                // 如果当前已经在代码块中，说明这一行是代码块结束，尝试识别流程图类型。
                Optional<DiagramType> diagramType = diagramType(fenceLang, fence.toString());
                // 若识别为 Mermaid/PlantUML，则创建视觉资产，不把源码直接混入正文。
                if (diagramType.isPresent()) {
                    // 创建流程图资产；渲染器不可用时会在资产上记录降级信息。
                    DocumentAsset asset = createDiagramAsset(ctx, root, assetIndex++, section, diagramType.get(), fence.toString());
                    // 将流程图资产加入待保存列表。
                    assets.add(asset);
                } else {
                    // 普通代码块不是视觉资产，需要还原 fenced code 写回当前章节正文。
                    sectionText.append("```").append(fenceLang).append('\n').append(fence).append("```\n\n");
                }
                // 代码块结束后，重置 inFence 状态。
                inFence = false;
                // 清空代码块语言，避免影响后续代码块。
                fenceLang = "";
                // 结束标记行已处理完，继续下一行。
                continue;
            }
            // 如果处于代码块内部，当前行只累积到 fence，不参与标题/图片解析。
            if (inFence) {
                // 保留代码块原始行内容和换行。
                fence.append(line).append('\n');
                // 代码块内部行处理完成，继续下一行。
                continue;
            }

            // 尝试匹配 Markdown 标题，用标题切分正文 section。
            Matcher headingMatcher = HEADING_PATTERN.matcher(line);
            // 如果当前行是标题，先结束上一个 section，再开启新 section。
            if (headingMatcher.matches()) {
                // 将上一个章节缓冲区 flush 为文本 Document，并拿到下一个 section 序号。
                sectionIndex = flushSection(textDocuments, sectionText, source, section, sectionIndex);
                // 更新当前章节名为标题文本。
                section = headingMatcher.group(2).strip();
                // 将标题行保留进新章节正文，避免正文 chunk 丢失标题上下文。
                sectionText.append(line).append('\n');
                // 标题行处理完成，继续下一行。
                continue;
            }

            // 处理当前行里的 Markdown 图片引用，并返回改写后的正文行和新增图片资产。
            ImageLineResult imageLineResult = processImagesInLine(ctx, markdownDir, root, line, section, assetIndex, imageCount);
            // 同步最新资产序号，保证后续资产顺序连续。
            assetIndex = imageLineResult.nextAssetIndex();
            // 同步最新图片计数，保证图片数量限制对整篇 Markdown 生效。
            imageCount = imageLineResult.nextImageCount();
            // 将当前行抽取到的图片资产加入待保存列表。
            assets.addAll(imageLineResult.assets());
            // 将图片语法改写后的文本行追加到当前章节正文。
            sectionText.append(imageLineResult.textLine()).append('\n');
        }

        // 文档遍历结束后，将最后一个章节缓冲区 flush 为文本 Document。
        flushSection(textDocuments, sectionText, source, section, sectionIndex);
        // 如果 Markdown 没有任何可用正文，也返回一个空 Document，保证后续流程有稳定输入。
        if (textDocuments.isEmpty()) {
            // 构造空正文 Document，并保留 source、section、section_index 元数据。
            textDocuments.add(new Document("", Map.of("source", source, "section", section, "section_index", 0)));
        }
        // 返回解析出的正文 Document 列表和视觉资产列表。
        return new ParsedMarkdown(textDocuments, assets);
    }

    private int flushSection(List<Document> textDocuments, StringBuilder sectionText, String source,
                             String section, int sectionIndex) {
        String text = sectionText.toString().strip();
        if (!text.isBlank()) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("source", source);
            metadata.put("section", section);
            metadata.put("section_index", sectionIndex);
            metadata.put("contentType", ChunkContentType.TEXT.name());
            textDocuments.add(new Document(text, metadata));
            sectionText.setLength(0);
            return sectionIndex + 1;
        }
        return sectionIndex;
    }

    private ImageLineResult processImagesInLine(IngestionContext ctx, Path markdownDir, Path root, String line,
                                                String section, int assetIndex, int imageCount) throws IOException {
        Matcher matcher = IMAGE_PATTERN.matcher(line);
        StringBuilder rewritten = new StringBuilder();
        List<DocumentAsset> assets = new ArrayList<>();
        int currentImageCount = imageCount;
        while (matcher.find()) {
            String altText = matcher.group(1).strip();
            ImageTarget target = parseImageTarget(matcher.group(2));
            String replacement = StringUtils.hasText(altText) ? altText : fileName(target.path());
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacement));

            if (!isSafeRelativePath(target.path())) {
                log.warn("Skipped unsafe Markdown image path documentId={}, path={}", ctx.getDocumentId(), target.path());
                continue;
            }
            if (++currentImageCount > maxImageCount) {
                throw new InvalidRequestException("Markdown 图片数量超过限制: " + maxImageCount);
            }
            Path imagePath = markdownDir.resolve(target.path()).normalize();
            if (!imagePath.startsWith(root) || !Files.isRegularFile(imagePath)) {
                log.warn("Markdown image missing documentId={}, path={}", ctx.getDocumentId(), target.path());
                continue;
            }
            long size = Files.size(imagePath);
            if (size > maxImageSizeMb * 1024 * 1024) {
                throw new InvalidRequestException("Markdown 图片大小超过限制: " + target.path());
            }
            String mimeType = Optional.ofNullable(Files.probeContentType(imagePath)).orElse("application/octet-stream");
            if (!mimeType.startsWith("image/")) {
                log.warn("Skipped non-image Markdown asset documentId={}, path={}, mimeType={}",
                        ctx.getDocumentId(), target.path(), mimeType);
                continue;
            }

            DocumentAsset asset = new DocumentAsset();
            asset.setId(UUID.randomUUID());
            asset.setDocumentId(ctx.getDocumentId());
            asset.setAssetType(AssetType.IMAGE);
            asset.setAssetIndex(assetIndex++);
            asset.setOriginalPath(target.path());
            asset.setObjectKey(objectKey(ctx, "assets", asset.getAssetIndex() + "-" + safeFileName(fileName(target.path()))));
            asset.setMimeType(mimeType);
            asset.setFileSize(size);
            asset.setSection(section);
            asset.setAltText(altText);
            asset.setTitle(StringUtils.hasText(target.title()) ? target.title() : replacement);
            asset.setStatus(AssetStatus.PENDING);
            asset.setContentHash(sha256(imagePath));
            asset.setMetadata(Map.of("source", "markdown-image"));
            objectStorageService.uploadFile(asset.getObjectKey(), imagePath, mimeType);
            assets.add(asset);
        }
        matcher.appendTail(rewritten);
        return new ImageLineResult(rewritten.toString(), assets, assetIndex, currentImageCount);
    }

    private DocumentAsset createDiagramAsset(IngestionContext ctx, Path root, int assetIndex, String section,
                                             DiagramType diagramType, String sourceCode) throws IOException {
        DocumentAsset asset = new DocumentAsset();
        asset.setId(UUID.randomUUID());
        asset.setDocumentId(ctx.getDocumentId());
        asset.setAssetType(AssetType.DIAGRAM);
        asset.setDiagramType(diagramType);
        asset.setAssetIndex(assetIndex);
        asset.setSection(section);
        asset.setTitle(section);
        asset.setSourceCode(sourceCode.strip());
        asset.setStatus(AssetStatus.PENDING);
        asset.setContentHash(sha256(sourceCode));
        asset.setMetadata(Map.of("source", "markdown-diagram"));

        Optional<Path> rendered = diagramRenderService.render(diagramType, sourceCode, root);
        if (rendered.isPresent()) {
            Path renderedPath = rendered.get();
            String mimeType = Optional.ofNullable(Files.probeContentType(renderedPath)).orElse("image/png");
            asset.setObjectKey(objectKey(ctx, "assets", assetIndex + "-" + diagramType.name().toLowerCase(Locale.ROOT) + ".png"));
            asset.setMimeType(mimeType);
            asset.setFileSize(Files.size(renderedPath));
            objectStorageService.uploadFile(asset.getObjectKey(), renderedPath, mimeType);
        } else {
            asset.setLastError("未配置流程图渲染器，已降级为源码检索");
        }
        return asset;
    }

    private Optional<DiagramType> diagramType(String fenceLang, String sourceCode) {
        String lang = fenceLang.toLowerCase(Locale.ROOT);
        if ("mermaid".equals(lang)) return Optional.of(DiagramType.MERMAID);
        if ("plantuml".equals(lang) || "puml".equals(lang)) return Optional.of(DiagramType.PLANTUML);
        if (sourceCode.stripLeading().startsWith("@startuml")) return Optional.of(DiagramType.PLANTUML);
        return Optional.empty();
    }

    private String imageReferenceText(DocumentAsset asset) {
        return """
                [图片引用]
                图片标题：%s
                所在章节：%s
                原始路径：%s
                替代文本：%s
                处理状态：%s
                [/图片引用]
                """.formatted(
                firstText(asset.getTitle(), asset.getAltText(), fileName(asset.getOriginalPath())),
                firstText(asset.getSection(), "未命名章节"),
                firstText(asset.getOriginalPath(), ""),
                firstText(asset.getAltText(), ""),
                asset.getStatus().name()).strip();
    }

    private String diagramSourceText(DocumentAsset asset) {
        return """
                [流程图源码说明]
                图标题：%s
                图类型：%s
                所在章节：%s
                流程关系：
                源码摘要：%s
                [/流程图源码说明]
                """.formatted(
                firstText(asset.getTitle(), asset.getSection(), "未命名流程图"),
                asset.getDiagramType() == null ? "" : asset.getDiagramType().name(),
                firstText(asset.getSection(), "未命名章节"),
                firstText(asset.getSourceCode(), "")).strip();
    }

    private ImageTarget parseImageTarget(String raw) {
        String value = raw.strip();
        if (value.startsWith("<") && value.contains(">")) {
            int end = value.indexOf('>');
            return new ImageTarget(value.substring(1, end), "");
        }
        int titleStart = value.indexOf(" \"");
        if (titleStart < 0) titleStart = value.indexOf(" '");
        if (titleStart < 0) return new ImageTarget(value, "");
        String path = value.substring(0, titleStart).strip();
        String title = value.substring(titleStart).strip();
        if ((title.startsWith("\"") && title.endsWith("\"")) || (title.startsWith("'") && title.endsWith("'"))) {
            title = title.substring(1, title.length() - 1);
        }
        return new ImageTarget(path, title);
    }

    private boolean isSafeRelativePath(String value) {
        if (!StringUtils.hasText(value)) return false;
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) return false;
        Path path = Paths.get(value);
        if (path.isAbsolute()) return false;
        Path normalized = path.normalize();
        return !normalized.startsWith("..");
    }

    private String objectKey(IngestionContext ctx, String category, String fileName) {
        return "documents/%s/%s/%s/%s".formatted(
                ctx.getSpaceId(), ctx.getDocumentId(), category, safeFileName(fileName));
    }

    private void deleteObjectQuietly(String objectKey) {
        if (!StringUtils.hasText(objectKey)) return;
        try {
            objectStorageService.deleteFile(objectKey);
        } catch (Exception e) {
            log.warn("删除视觉资产对象失败: objectKey={}", objectKey, e);
        }
    }

    private String safeFileName(String name) {
        if (!StringUtils.hasText(name)) return "unknown";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private String titleFromFile(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String fileName(String path) {
        if (!StringUtils.hasText(path)) return "";
        return Paths.get(path).getFileName().toString();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value;
        }
        return "";
    }

    private String normalizeSection(String section) {
        return StringUtils.hasText(section) ? section : "";
    }

    private String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private String sha256(String value) {
        return sha256(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算内容 hash 失败", e);
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete temp path {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to delete temp directory {}", dir, e);
        }
    }

    private record ParsedMarkdown(List<Document> textDocuments, List<DocumentAsset> assets) {
    }

    private record ImageLineResult(String textLine, List<DocumentAsset> assets, int nextAssetIndex, int nextImageCount) {
    }

    private record ImageTarget(String path, String title) {
    }
}
