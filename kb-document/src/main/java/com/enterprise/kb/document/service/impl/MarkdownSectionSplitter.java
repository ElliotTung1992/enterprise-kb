package com.enterprise.kb.document.service.impl;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown parent section 切分器。
 */
@Component
class MarkdownSectionSplitter {

    /**
     * 按配置标题层级切分 parent section。
     *
     * @param markdown           Markdown 原文
     * @param splitHeadingLevels 切分到第几级标题
     * @return parent section 切片
     */
    List<SectionSlice> split(String markdown, int splitHeadingLevels) {
        List<Heading> headings = new ArrayList<>();
        Matcher matcher = headingPattern(splitHeadingLevels).matcher(markdown);
        while (matcher.find()) {
            headings.add(new Heading(matcher.start(), matcher.end(), matcher.group(1).length(), matcher.group(2).trim()));
        }
        if (headings.isEmpty()) {
            return List.of(new SectionSlice("全文", 0, markdown, 0, markdown.length()));
        }

        List<SectionSlice> sections = new ArrayList<>();
        String[] path = new String[Math.min(6, Math.max(1, splitHeadingLevels))];
        if (headings.getFirst().start() > 0 && !markdown.substring(0, headings.getFirst().start()).isBlank()) {
            sections.add(new SectionSlice("引言", 0, markdown.substring(0, headings.getFirst().start()).strip(),
                    0, headings.getFirst().start()));
        }
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            path[heading.level() - 1] = heading.title();
            for (int j = heading.level(); j < path.length; j++) {
                path[j] = null;
            }
            int end = i + 1 < headings.size() ? headings.get(i + 1).start() : markdown.length();
            String content = markdown.substring(heading.start(), end).strip();
            sections.add(new SectionSlice(breadcrumb(path), heading.level(), content, heading.start(), end));
        }
        return sections;
    }

    private Pattern headingPattern(int splitHeadingLevels) {
        int levels = Math.min(6, Math.max(1, splitHeadingLevels));
        return Pattern.compile("(?m)^(#{1," + levels + "})\\s+(.+?)\\s*$");
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

    private record Heading(int start, int end, int level, String title) {}

    /**
     * parent section 的原文切片。
     *
     * @param section      标题面包屑路径
     * @param headingLevel 当前 section 对应的标题层级，无标题引言为 0
     * @param content      section 的完整 Markdown 原文
     * @param charStart    section 在原始 Markdown 中的起始字符下标
     * @param charEnd      section 在原始 Markdown 中的结束字符下标
     */
    record SectionSlice(String section, int headingLevel, String content, int charStart, int charEnd) {}
}
