package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.model.MdDocumentAsset;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown child chunk 原子块解析与打包器。
 */
@Component
class MarkdownChildPacker {

    private static final Pattern H4_H6 = Pattern.compile("^#{4,6}\\s+.*");
    private static final Pattern IMAGE_LINE = Pattern.compile("^\\s*!\\[([^]]*)]\\((\\S+)(?:\\s+\"([^\"]*)\")?\\)\\s*$");
    private static final String IMAGE_END_MARKER = "[/图片说明]";

    /**
     * 将 parent 内容打包为 child 切片。
     *
     * @param content   parent Markdown 内容
     * @param images    图片语义块位置
     * @param maxTokens 单个 child 的目标 token 上限
     * @param minTokens 普通文本 child 的目标 token 下限
     * @return child 切片
     */
    List<ChildSlice> pack(String content, List<ImageBlock> images, int maxTokens, int minTokens) {
        PackingConfig config = new PackingConfig(maxTokens, minTokens);
        return packBlocks(atomicBlocks(content, images, config), config);
    }

    private List<AtomicBlock> atomicBlocks(String content, List<ImageBlock> images, PackingConfig config) {
        List<Line> lines = linesWithOffsets(content);
        Map<Integer, MdDocumentAsset> imageByStart = new HashMap<>();
        for (ImageBlock image : images) {
            imageByStart.put(image.start(), image.asset());
        }
        List<AtomicBlock> blocks = new ArrayList<>();
        for (int i = 0; i < lines.size(); ) {
            Line line = lines.get(i);
            if (line.text().isBlank()) {
                i++;
                continue;
            }
            MdDocumentAsset image = imageByStart.get(line.start());
            if (image != null) {
                CollectResult result = collectImageBlock(lines, i);
                blocks.add(new AtomicBlock(result.raw().strip(), result.raw().strip(),
                        result.start(), result.end(), false, true, image));
                i = result.nextIndex();
                continue;
            }
            if (line.text().stripLeading().startsWith("```")) {
                CollectResult result = collectCodeFence(lines, i);
                blocks.add(toAtomicBlock(result.raw(), result.start(), result.end()));
                i = result.nextIndex();
                continue;
            }
            if (isTableStart(lines, i)) {
                CollectResult result = collectWhile(lines, i, item -> isTableLine(item.text()));
                blocks.addAll(tableBlocks(result.raw(), result.start(), config));
                i = result.nextIndex();
                continue;
            }
            if (isListLine(line.text())) {
                CollectResult result = collectWhile(lines, i, item -> item.text().isBlank() || isListLine(item.text()));
                blocks.addAll(splitIfNeeded(result.raw().strip(), result.start(), config));
                i = result.nextIndex();
                continue;
            }
            if (H4_H6.matcher(line.text().strip()).matches() && i + 1 < lines.size()) {
                CollectResult result = collectParagraph(lines, i);
                blocks.addAll(splitIfNeeded(result.raw().strip(), result.start(), config));
                i = result.nextIndex();
                continue;
            }
            CollectResult result = collectParagraph(lines, i);
            blocks.addAll(splitIfNeeded(result.raw().strip(), result.start(), config));
            i = result.nextIndex();
        }
        return blocks;
    }

    private List<AtomicBlock> splitIfNeeded(String text, int start, PackingConfig config) {
        if (MarkdownTokenEstimator.estimate(text) > config.maxTokens() && !isCodeFence(text) && !isTable(text)) {
            return splitOversizedText(text, start, config);
        }
        return List.of(toAtomicBlock(text, start, start + text.length()));
    }

    private AtomicBlock toAtomicBlock(String text, int start, int end) {
        String stripped = text.strip();
        return new AtomicBlock(stripped, stripped, start, end, false, false, null);
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

    private CollectResult collectImageBlock(List<Line> lines, int startIndex) {
        int endIndex = startIndex + 1;
        while (endIndex < lines.size()) {
            if (lines.get(endIndex).text().strip().equals(IMAGE_END_MARKER)) {
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
        return stripped.startsWith("```") || IMAGE_LINE.matcher(stripped).matches()
                || isTableCandidateLine(stripped) || isListLine(stripped);
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

    private List<AtomicBlock> splitOversizedText(String text, int baseStart, PackingConfig config) {
        if (looksLikeList(text)) {
            return splitByLines(text, baseStart, config);
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
            if (!current.isEmpty() && MarkdownTokenEstimator.estimate(current + sentence) > config.maxTokens()) {
                result.add(new AtomicBlock(current.toString().strip(), current.toString().strip(),
                        baseStart + currentStart, baseStart + matcher.start(), false, false, null));
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
                    baseStart + currentStart, baseStart + text.length(), false, false, null));
        }
        return result;
    }

    private List<AtomicBlock> splitByLines(String text, int baseStart, PackingConfig config) {
        List<AtomicBlock> result = new ArrayList<>();
        String[] lines = text.split("\\R");
        StringBuilder current = new StringBuilder();
        int offset = 0;
        int currentStart = 0;
        for (String line : lines) {
            String candidate = current + (current.isEmpty() ? "" : "\n") + line;
            if (!current.isEmpty() && MarkdownTokenEstimator.estimate(candidate) > config.maxTokens()) {
                result.add(new AtomicBlock(current.toString(), current.toString(),
                        baseStart + currentStart, baseStart + offset, false, false, null));
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
                    baseStart + currentStart, baseStart + text.length(), false, false, null));
        }
        return result;
    }

    private List<ChildSlice> packBlocks(List<AtomicBlock> blocks, PackingConfig config) {
        List<ChildSlice> slices = new ArrayList<>();
        List<AtomicBlock> current = new ArrayList<>();
        for (AtomicBlock block : blocks) {
            if (block.image()) {
                if (!current.isEmpty()) {
                    addSlice(slices, current);
                    current = new ArrayList<>();
                }
                addSlice(slices, List.of(block));
                continue;
            }
            int currentTokens = tokenSum(current);
            int nextTokens = currentTokens + MarkdownTokenEstimator.estimate(block.embedText());
            if (!current.isEmpty() && nextTokens > config.maxTokens()) {
                if (currentTokens >= config.minTokens()) {
                    addSlice(slices, current);
                    current = new ArrayList<>();
                } else if (!slices.isEmpty()) {
                    mergeIntoPrevious(slices, current);
                    current = new ArrayList<>();
                }
            }
            current.add(block);
        }
        if (!current.isEmpty()) {
            if (tokenSum(current) < config.minTokens() && !slices.isEmpty() && slices.getLast().asset() == null) {
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
        MdDocumentAsset asset = blocks.size() == 1 ? blocks.getFirst().asset() : null;
        slices.add(new ChildSlice(rawText, embedText, blocks.getFirst().start(), blocks.getLast().end(), asset));
    }

    private void mergeIntoPrevious(List<ChildSlice> slices, List<AtomicBlock> blocks) {
        ChildSlice previous = slices.removeLast();
        String rawText = previous.rawText() + "\n\n" + joinRaw(blocks);
        String embedText = previous.embedText() + "\n\n" + joinEmbed(blocks);
        slices.add(new ChildSlice(rawText, embedText, previous.charStart(), blocks.getLast().end(), null));
    }

    private String joinRaw(List<AtomicBlock> blocks) {
        return String.join("\n\n", blocks.stream().map(AtomicBlock::rawText).toList());
    }

    private String joinEmbed(List<AtomicBlock> blocks) {
        return String.join("\n\n", blocks.stream().map(AtomicBlock::embedText).toList());
    }

    private int tokenSum(List<AtomicBlock> blocks) {
        return blocks.stream().mapToInt(block -> MarkdownTokenEstimator.estimate(block.embedText())).sum();
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
        List<String> rows = text.lines().map(String::strip).filter(line -> !line.isBlank()).toList();
        if (rows.isEmpty()) {
            return text;
        }
        List<String> headers = cells(rows.getFirst());
        List<String> values = rows.stream()
                .skip(1)
                .filter(row -> !isSeparatorLine(row))
                .map(row -> linearizeRow(headers, cells(row)))
                .filter(row -> !row.isBlank())
                .toList();
        return values.isEmpty() ? text : String.join("\n", values);
    }

    private List<AtomicBlock> tableBlocks(String tableText, int baseStart, PackingConfig config) {
        String linearized = linearizeTable(tableText);
        if (MarkdownTokenEstimator.estimate(linearized) <= config.maxTokens()) {
            return List.of(new AtomicBlock(tableText.strip(), linearized,
                    baseStart, baseStart + tableText.length(), true, false, null));
        }
        List<Line> lines = linesWithOffsets(tableText);
        if (lines.size() <= 2) {
            return List.of(new AtomicBlock(tableText.strip(), linearized,
                    baseStart, baseStart + tableText.length(), true, false, null));
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
            if (!currentLines.isEmpty() && MarkdownTokenEstimator.estimate(candidate) > config.maxTokens()) {
                blocks.add(tableBlock(currentLines, currentEmbeds, baseStart));
                currentLines = new ArrayList<>();
                currentEmbeds = new ArrayList<>();
            }
            currentLines.add(row);
            currentEmbeds.add(embed);
        }
        if (!currentLines.isEmpty()) {
            blocks.add(tableBlock(currentLines, currentEmbeds, baseStart));
        }
        return blocks;
    }

    private AtomicBlock tableBlock(List<Line> lines, List<String> embeds, int baseStart) {
        String raw = String.join("\n", lines.stream().map(Line::text).toList());
        String embed = String.join("\n", embeds);
        return new AtomicBlock(raw, embed,
                baseStart + lines.getFirst().start(), baseStart + lines.getLast().end(), true, false, null);
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

    private record PackingConfig(int maxTokens, int minTokens) {}

    /**
     * 图片语义块在增强 parent content 中的位置。
     *
     * @param start 图片块起始字符下标
     * @param end   图片块结束字符下标
     * @param asset 图片资产
     */
    record ImageBlock(int start, int end, MdDocumentAsset asset) {}

    /**
     * 已打包完成的 child 切片。
     *
     * @param rawText   child 对应的原始 Markdown 文本
     * @param embedText child 入库检索文本
     * @param charStart child 在 parent 原文中的起始字符下标
     * @param charEnd   child 在 parent 原文中的结束字符下标
     * @param asset     图片 child 关联的资产，普通文本为 null
     */
    record ChildSlice(String rawText, String embedText, int charStart, int charEnd, MdDocumentAsset asset) {}

    /**
     * child 打包前的原子块。
     *
     * @param rawText   返回给 LLM 时使用的原始 Markdown 文本
     * @param embedText 用于 embedding 和关键词检索的文本
     * @param start     原子块在 parent 原文中的起始字符下标
     * @param end       原子块在 parent 原文中的结束字符下标
     * @param table     是否为表格原子块
     * @param image     是否为图片语义原子块，图片块必须单独成 child
     * @param asset     图片语义块关联的 Markdown 图片资产，非图片块为 null
     */
    private record AtomicBlock(String rawText, String embedText, int start, int end, boolean table,
                               boolean image, MdDocumentAsset asset) {}

    private record Line(String text, int start, int end) {}

    private record CollectResult(String raw, int start, int end, int nextIndex) {}
}
