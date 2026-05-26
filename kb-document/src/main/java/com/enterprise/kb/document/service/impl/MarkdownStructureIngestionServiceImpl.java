package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;
import com.enterprise.kb.document.model.MdChildChunk;
import com.enterprise.kb.document.model.MdParentChunk;
import com.enterprise.kb.document.service.MarkdownStructureIngestionService;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private static final Pattern H4_H6 = Pattern.compile("^#{4,6}\\s+.*");
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final int DEFAULT_MIN_TOKENS = 64;

    @Value("${enterprise.kb.chunking.markdown.max-tokens:512}")
    private int maxTokens = DEFAULT_MAX_TOKENS;

    @Value("${enterprise.kb.chunking.markdown.min-tokens:64}")
    private int minTokens = DEFAULT_MIN_TOKENS;

    /** 切分到第几级标题（默认 H1-H3），由配置驱动 parent 切分粒度。 */
    @Value("${enterprise.kb.chunking.markdown.split-heading-levels:3}")
    private int splitHeadingLevels = 3;

    /** 按 {@code splitHeadingLevels} 构造 parent 切分用的标题正则。 */
    private Pattern headingPattern() {
        int levels = Math.min(6, Math.max(1, splitHeadingLevels));
        return Pattern.compile("(?m)^(#{1," + levels + "})\\s+(.+?)\\s*$");
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
        // 按文件格式切分
        List<SectionSlice> sections = splitParents(markdown);
        List<MdParentChunk> parents = new ArrayList<>();
        List<MdChildChunk> children = new ArrayList<>();
        List<Document> vectorDocuments = new ArrayList<>();

        for (int i = 0; i < sections.size(); i++) {
            SectionSlice section = sections.get(i);
            // 构建parent-chunk
            MdParentChunk parent = toParent(documentId, spaceId, section, i);
            // parent-chunk切child-chunk
            List<MdChildChunk> childChunks = splitChildren(parent, documentId, spaceId);
            parent.setChildCount(childChunks.size());
            parents.add(parent);
            children.addAll(childChunks);
            for (MdChildChunk child : childChunks) {
                vectorDocuments.add(toVectorDocument(child));
            }
        }
        return new MarkdownStructureIngestionResult(parents, children, vectorDocuments);
    }

    private String readFile(String filePath) {
        try {
            return Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Markdown 文件失败: " + filePath, e);
        }
    }

    private List<SectionSlice> splitParents(String markdown) {
        List<Heading> headings = new ArrayList<>();
        // 按3级标题切分
        Matcher matcher = headingPattern().matcher(markdown);
        while (matcher.find()) {
            headings.add(new Heading(matcher.start(), matcher.end(), matcher.group(1).length(), matcher.group(2).trim()));
        }
        if (headings.isEmpty()) {
            return List.of(new SectionSlice("全文", 0, markdown, 0, markdown.length()));
        }

        List<SectionSlice> sections = new ArrayList<>();
        // 面包屑路径
        String[] path = new String[3];
        // 如果开头的数据没有标题做兜底
        if (headings.getFirst().start() > 0 && !markdown.substring(0, headings.getFirst().start()).isBlank()) {
            sections.add(new SectionSlice("引言", 0, markdown.substring(0, headings.getFirst().start()).strip(),
                    0, headings.getFirst().start()));
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            // 设置面包屑数据
            path[heading.level() - 1] = heading.title();
            for (int j = heading.level(); j < path.length; j++) {
                path[j] = null;
            }
            // 切割文本时的end下标
            int end = i + 1 < headings.size() ? headings.get(i + 1).start() : markdown.length();
            String content = markdown.substring(heading.start(), end).strip();
            sections.add(new SectionSlice(breadcrumb(path), heading.level(), content, heading.start(), end));
        }
        return sections;
    }

    private String breadcrumb(String[] path) {
        List<String> parts = new ArrayList<>();
        for (String item : path) {
            if (item != null && !item.isBlank()) {
                parts.add(item);
            }
        }
        return String.join(" > ", parts);
    }

    private MdParentChunk toParent(UUID documentId, UUID spaceId, SectionSlice section, int ordinal) {
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

    private List<MdChildChunk> splitChildren(MdParentChunk parent, UUID documentId, UUID spaceId) {
        // 行数据转块
        List<AtomicBlock> blocks = atomicBlocks(parent.getContent());
        // 打包数据
        List<ChildSlice> slices = packBlocks(blocks);
        List<MdChildChunk> children = new ArrayList<>();
        // 构建child-chunk集合
        for (int i = 0; i < slices.size(); i++) {
            ChildSlice slice = slices.get(i);
            MdChildChunk child = new MdChildChunk();
            child.setId(UUID.randomUUID());
            child.setParentId(parent.getId());
            child.setDocumentId(documentId);
            child.setSpaceId(spaceId);
            child.setSection(parent.getSection());
            child.setSeqInParent(i);
            child.setEmbedText(slice.embedText());
            child.setTokenCount(estimateTokens(slice.embedText()));
            child.setCharStart(slice.charStart());
            child.setCharEnd(slice.charEnd());
            child.setCreatedAt(Instant.now());
            children.add(child);
        }
        return children;
    }

    private List<AtomicBlock> atomicBlocks(String content) {
        // 解析成一行一行的数据
        List<Line> lines = linesWithOffsets(content);
        List<AtomicBlock> blocks = new ArrayList<>();
        for (int i = 0; i < lines.size(); ) {
            Line line = lines.get(i);
            if (line.text().isBlank()) {
                i++;
                continue;
            }
            // 处理代码块 - 代码块不被分割
            if (line.text().stripLeading().startsWith("```")) {
                CollectResult result = collectCodeFence(lines, i);
                blocks.add(toAtomicBlock(result.raw(), result.start(), result.end()));
                i = result.nextIndex();
                continue;
            }
            // 处理表格
            if (isTableStart(lines, i)) {
                CollectResult result = collectWhile(lines, i, item -> isTableLine(item.text()));
                // 切分表格数据
                blocks.addAll(tableBlocks(result.raw(), result.start()));
                i = result.nextIndex();
                continue;
            }
            // 处理列表行
            if (isListLine(line.text())) {
                CollectResult result = collectWhile(lines, i, item -> item.text().isBlank() || isListLine(item.text()));
                blocks.addAll(splitIfNeeded(result.raw().strip(), result.start()));
                i = result.nextIndex();
                continue;
            }
            // 处理小标题
            if (H4_H6.matcher(line.text().strip()).matches() && i + 1 < lines.size()) {
                CollectResult result = collectParagraph(lines, i);
                blocks.addAll(splitIfNeeded(result.raw().strip(), result.start()));
                i = result.nextIndex();
                continue;
            }
            CollectResult result = collectParagraph(lines, i);
            blocks.addAll(splitIfNeeded(result.raw().strip(), result.start()));
            i = result.nextIndex();
        }
        return blocks;
    }

    private List<AtomicBlock> splitIfNeeded(String text, int start) {
        if (estimateTokens(text) > maxTokens && !isCodeFence(text) && !isTable(text)) {
            return splitOversizedText(text, start);
        }
        return List.of(toAtomicBlock(text, start, start + text.length()));
    }

    private AtomicBlock toAtomicBlock(String text, int start, int end) {
        String stripped = text.strip();
        return new AtomicBlock(stripped, stripped, start, end, false);
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

    private CollectResult collectCodeFence(List<Line> lines, int startIndex) {
        int endIndex = startIndex + 1;
        while (endIndex < lines.size()) {
            if (lines.get(endIndex).text().stripLeading().startsWith("```")) {
                endIndex++;
                break;
            }
            endIndex++;
        }
        return collectRange(lines, startIndex, endIndex);
    }

    private CollectResult collectParagraph(List<Line> lines, int startIndex) {
        int endIndex = startIndex;
        while (endIndex < lines.size() && !lines.get(endIndex).text().isBlank()) {
            if (endIndex > startIndex && isStructuralStart(lines.get(endIndex).text())) {
                break;
            }
            endIndex++;
        }
        return collectRange(lines, startIndex, endIndex);
    }

    private CollectResult collectWhile(List<Line> lines, int startIndex, java.util.function.Predicate<Line> predicate) {
        int endIndex = startIndex;
        while (endIndex < lines.size() && predicate.test(lines.get(endIndex))) {
            endIndex++;
        }
        return collectRange(lines, startIndex, endIndex);
    }

    private CollectResult collectRange(List<Line> lines, int startIndex, int endIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < endIndex; i++) {
            sb.append(lines.get(i).text());
            if (lines.get(i).end() <= lines.getLast().end() && lines.get(i).end() > lines.get(i).start()) {
                sb.append('\n');
            }
        }
        return new CollectResult(sb.toString().stripTrailing(),
                lines.get(startIndex).start(), lines.get(endIndex - 1).end(), endIndex);
    }

    private boolean isStructuralStart(String text) {
        String stripped = text.stripLeading();
        return stripped.startsWith("```") || isTableCandidateLine(stripped) || isListLine(stripped);
    }

    private boolean isTableStart(List<Line> lines, int index) {
        if (!isTableCandidateLine(lines.get(index).text())) {
            return false;
        }
        if (index + 1 >= lines.size()) {
            return false;
        }
        Line next = lines.get(index + 1);
        return isSeparatorLine(next.text()) || isTableCandidateLine(next.text());
    }

    private boolean isTableLine(String text) {
        return isTableCandidateLine(text) || isSeparatorLine(text);
    }

    private boolean isTableCandidateLine(String text) {
        String stripped = text.strip();
        long pipeCount = stripped.chars().filter(ch -> ch == '|').count();
        return pipeCount >= 2 && (stripped.startsWith("|") || stripped.endsWith("|"));
    }

    private boolean isListLine(String text) {
        return text.matches("^\\s*([-*+] |\\d+\\. ).+");
    }

    private List<AtomicBlock> splitOversizedText(String text, int baseStart) {
        if (looksLikeList(text)) {
            return splitByLines(text, baseStart);
        }
        List<AtomicBlock> result = new ArrayList<>();
        int cursor = 0;
        Matcher matcher = Pattern.compile("[^。！？.!?]+[。！？.!?]?").matcher(text);
        StringBuilder current = new StringBuilder();
        int currentStart = 0;
        while (matcher.find()) {
            String sentence = matcher.group();
            if (current.isEmpty()) {
                currentStart = matcher.start();
            }
            if (!current.isEmpty() && estimateTokens(current + sentence) > maxTokens) {
                result.add(new AtomicBlock(current.toString().strip(), current.toString().strip(),
                        baseStart + currentStart, baseStart + matcher.start(), false));
                current.setLength(0);
                currentStart = matcher.start();
            }
            current.append(sentence);
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            current.append(text.substring(cursor));
        }
        if (!current.isEmpty()) {
            result.add(new AtomicBlock(current.toString().strip(), current.toString().strip(),
                    baseStart + currentStart, baseStart + text.length(), false));
        }
        return result;
    }

    private List<AtomicBlock> splitByLines(String text, int baseStart) {
        List<AtomicBlock> result = new ArrayList<>();
        String[] lines = text.split("\\R");
        StringBuilder current = new StringBuilder();
        int offset = 0;
        int currentStart = 0;
        for (String line : lines) {
            String candidate = current + (current.isEmpty() ? "" : "\n") + line;
            if (!current.isEmpty() && estimateTokens(candidate) > maxTokens) {
                result.add(new AtomicBlock(current.toString(), current.toString(),
                        baseStart + currentStart, baseStart + offset, false));
                current.setLength(0);
                currentStart = offset;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
            offset += line.length() + 1;
        }
        if (!current.isEmpty()) {
            result.add(new AtomicBlock(current.toString(), current.toString(),
                    baseStart + currentStart, baseStart + text.length(), false));
        }
        return result;
    }

    private List<ChildSlice> packBlocks(List<AtomicBlock> blocks) {
        List<ChildSlice> slices = new ArrayList<>();
        List<AtomicBlock> current = new ArrayList<>();
        for (AtomicBlock block : blocks) {
            int currentTokens = tokenSum(current);
            int nextTokens = currentTokens + estimateTokens(block.embedText());
            // 仅当再并入当前块会超 max 时，才给已累积的 current 收口；否则继续贪心累积，
            // 让 current 长到接近 max（避免小块还没攒到 min 就被过早合并）。
            if (!current.isEmpty() && nextTokens > maxTokens) {
                if (currentTokens >= minTokens) {
                    // current 已达标：独立成块（max 是软目标，不跨块借句填满，宁可稍欠）
                    addSlice(slices, current);
                    current = new ArrayList<>();
                } else if (!slices.isEmpty()) {
                    // 孤儿且攒不大：后向并入前一个已成型的兄弟 child，下一块照常起新 child
                    mergeIntoPrevious(slices, current);
                    current = new ArrayList<>();
                }
                // else：孤儿且是 section 首块、无前序兄弟 → 不收口，前向并入下一块（一起成块，允许超 max）
            }
            current.add(block);
        }
        if (!current.isEmpty()) {
            // 尾部孤儿：最后一个 child 不足 min 且有前序兄弟 → 后向并入前一兄弟
            if (tokenSum(current) < minTokens && !slices.isEmpty()) {
                mergeIntoPrevious(slices, current);
            } else {
                addSlice(slices, current);
            }
        }
        return slices;
    }

    private void addSlice(List<ChildSlice> slices, List<AtomicBlock> blocks) {
        String rawText = joinRaw(blocks);
        String embedText = joinEmbed(blocks);
        slices.add(new ChildSlice(rawText, embedText, blocks.getFirst().start(), blocks.getLast().end()));
    }

    private void mergeIntoPrevious(List<ChildSlice> slices, List<AtomicBlock> blocks) {
        ChildSlice previous = slices.removeLast();
        String rawText = previous.rawText() + "\n\n" + joinRaw(blocks);
        String embedText = previous.embedText() + "\n\n" + joinEmbed(blocks);
        slices.add(new ChildSlice(rawText, embedText, previous.charStart(), blocks.getLast().end()));
    }

    private String joinRaw(List<AtomicBlock> blocks) {
        return String.join("\n\n", blocks.stream().map(AtomicBlock::rawText).toList());
    }

    private String joinEmbed(List<AtomicBlock> blocks) {
        return String.join("\n\n", blocks.stream().map(AtomicBlock::embedText).toList());
    }

    private int tokenSum(List<AtomicBlock> blocks) {
        return blocks.stream().mapToInt(block -> estimateTokens(block.embedText())).sum();
    }

    private boolean isCodeFence(String text) {
        return text.stripLeading().startsWith("```");
    }

    private boolean looksLikeList(String text) {
        return text.lines().filter(line -> line.matches("^\\s*([-*+] |\\d+\\. ).+")).count() >= 2;
    }

    private boolean isTable(String text) {
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        return lines.size() >= 2 && lines.stream().allMatch(line -> line.contains("|"));
    }

    private String linearizeTable(String text) {
        // 获取非空行数据
        List<String> rows = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        if (rows.isEmpty()) {
            return text;
        }
        // 获取表头
        List<String> headers = cells(rows.getFirst());
        List<String> values = rows.stream()
                .skip(1)
                // 跳过制表符
                .filter(row -> !isSeparatorLine(row))
                // 数据和表头连接
                .map(row -> linearizeRow(headers, cells(row)))
                .filter(row -> !row.isBlank())
                .toList();
        return values.isEmpty() ? text : String.join("\n", values);
    }

    private List<AtomicBlock> tableBlocks(String tableText, int baseStart) {
        String linearized = linearizeTable(tableText);
        // 没超限制
        if (estimateTokens(linearized) <= maxTokens) {
            return List.of(new AtomicBlock(tableText.strip(), linearized,
                    baseStart, baseStart + tableText.length(), true));
        }
        // 如果只有2行数据
        List<Line> lines = linesWithOffsets(tableText);
        if (lines.size() <= 2) {
            return List.of(new AtomicBlock(tableText.strip(), linearized,
                    baseStart, baseStart + tableText.length(), true));
        }

        List<String> headers = cells(lines.getFirst().text());
        int dataStartIndex = isSeparatorLine(lines.get(1).text()) ? 2 : 1;
        List<AtomicBlock> blocks = new ArrayList<>();
        List<Line> currentLines = new ArrayList<>();
        List<String> currentEmbeds = new ArrayList<>();
        for (int i = dataStartIndex; i < lines.size(); i++) {
            Line row = lines.get(i);
            String embed = linearizeRow(headers, cells(row.text()));
            String candidate = String.join("\n", currentEmbeds)
                    + (currentEmbeds.isEmpty() ? "" : "\n") + embed;
            // 切分表格
            if (!currentLines.isEmpty() && estimateTokens(candidate) > maxTokens) {
                blocks.add(tableBlock(currentLines, currentEmbeds, baseStart));
                currentLines = new ArrayList<>();
                currentEmbeds = new ArrayList<>();
            }
            currentLines.add(row);
            currentEmbeds.add(embed);
        }
        // 兜底处理
        if (!currentLines.isEmpty()) {
            blocks.add(tableBlock(currentLines, currentEmbeds, baseStart));
        }
        return blocks;
    }

    private AtomicBlock tableBlock(List<Line> lines, List<String> embeds, int baseStart) {
        String raw = String.join("\n", lines.stream().map(Line::text).toList());
        String embed = String.join("\n", embeds);
        return new AtomicBlock(raw, embed,
                baseStart + lines.getFirst().start(), baseStart + lines.getLast().end(), true);
    }

    private boolean isSeparatorLine(String row) {
        return row.strip().matches("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$");
    }

    private List<String> cells(String row) {
        String normalized = row;
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return Pattern.compile("\\|").splitAsStream(normalized).map(String::strip).toList();
    }

    private String linearizeRow(List<String> headers, List<String> values) {
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String header = i < headers.size() && !headers.get(i).isBlank() ? headers.get(i) : "列" + (i + 1);
            pairs.add(header + ": " + values.get(i));
        }
        return String.join("；", pairs);
    }

    private Document toVectorDocument(MdChildChunk child) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", child.getDocumentId().toString());
        metadata.put("spaceId", child.getSpaceId().toString());
        metadata.put("parentId", child.getParentId().toString());
        metadata.put("section", child.getSection());
        metadata.put("seqInParent", child.getSeqInParent());
        String text = (child.getSection() == null || child.getSection().isBlank())
                ? child.getEmbedText()
                : child.getSection() + "\n\n" + child.getEmbedText();
        return new Document(child.getId().toString(), text, metadata);
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        long cjk = text.chars().filter(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN).count();
        long nonCjk = text.length() - cjk;
        return Math.max(1, (int) (cjk + Math.ceil(nonCjk / 4.0)));
    }

    /**
     * Markdown H1-H3 标题位置。
     *
     * @param start 标题行在原始 Markdown 中的起始字符下标
     * @param end   标题行在原始 Markdown 中的结束字符下标
     * @param level 标题层级，取值 1-3
     * @param title 去掉 # 后的标题文本
     */
    private record Heading(int start, int end, int level, String title) {}

    /**
     * parent section 的原文切片。
     *
     * @param section      H1-H3 面包屑路径
     * @param headingLevel 当前 section 对应的标题层级，无标题引言为 0
     * @param content      section 的完整 Markdown 原文
     * @param charStart    section 在原始 Markdown 中的起始字符下标
     * @param charEnd      section 在原始 Markdown 中的结束字符下标
     */
    private record SectionSlice(String section, int headingLevel, String content, int charStart, int charEnd) {}

    /**
     * child 打包前的原子块。
     *
     * @param rawText   返回给 LLM 时使用的原始 Markdown 文本
     * @param embedText 用于 embedding 和关键词检索的文本
     * @param start     原子块在 parent 原文中的起始字符下标
     * @param end       原子块在 parent 原文中的结束字符下标
     * @param table     是否为表格原子块
     */
    private record AtomicBlock(String rawText, String embedText, int start, int end, boolean table) {}

    /**
     * 已打包完成的 child 切片。
     *
     * @param rawText   child 对应的原始 Markdown 文本
     * @param embedText child 入库检索文本
     * @param charStart child 在 parent 原文中的起始字符下标
     * @param charEnd   child 在 parent 原文中的结束字符下标
     */
    private record ChildSlice(String rawText, String embedText, int charStart, int charEnd) {}

    /**
     * 带字符位置的 Markdown 单行。
     *
     * @param text  行文本，不包含换行符
     * @param start 行在当前内容中的起始字符下标
     * @param end   行在当前内容中的结束字符下标，包含换行符位置
     */
    private record Line(String text, int start, int end) {}

    /**
     * 连续行收集结果。
     *
     * @param raw       收集到的原始文本
     * @param start     收集区间起始字符下标
     * @param end       收集区间结束字符下标
     * @param nextIndex 下一次扫描应继续处理的行下标
     */
    private record CollectResult(String raw, int start, int end, int nextIndex) {}
}
