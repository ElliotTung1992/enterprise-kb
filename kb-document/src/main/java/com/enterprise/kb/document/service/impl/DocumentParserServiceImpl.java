package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.DocumentParserService;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;

/**
 * 文档解析服务实现，支持 PDF、HTML 和通用文件（通过 Tika）三种解析方式。
 * <p>PDF 优先使用 PagePdfDocumentReader，解析失败时 fallback 到 Tika。
 * HTML 使用 Jsoup 直接解析，提取元数据并按标题层级切分为多个 Section 文档。
 * 解析结果为 Spring AI Document 列表，可直接用于分块和向量化。</p>
 */
@Slf4j
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    private static final Set<String> HEADING_TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> BLOCK_TAGS = Set.of(
            "p", "div", "li", "td", "th", "dt", "dd", "blockquote", "figcaption", "article", "section", "main"
    );

    /**
     * 解析文件为 Spring AI Document 列表。
     * <p>PDF 使用 PagePdfDocumentReader，HTML 使用 Jsoup，其余使用 Tika。</p>
     *
     * @param filePath 文件路径
     * @param mimeType MIME 类型
     * @return 解析后的文档列表
     */
    @Override
    public List<Document> parse(String filePath, String mimeType) {
        log.debug("Parsing file {} with mimeType {}", filePath, mimeType);
        return switch (mimeType) {
            case "application/pdf" -> parsePdf(filePath);
            case "text/html" -> parseHtml(filePath);
            default -> parseTika(filePath);
        };
    }

    // ── PDF ──────────────────────────────────────────────────────────────────

    private List<Document> parsePdf(String filePath) {
        try {
            PagePdfDocumentReader reader = new PagePdfDocumentReader(
                    new FileSystemResource(filePath),
                    PdfDocumentReaderConfig.builder()
                            .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                            .withPagesPerDocument(1)
                            .build());
            return reader.get();
        } catch (Exception e) {
            log.warn("PDF parsing failed, falling back to Tika: {}", e.getMessage());
            return parseTika(filePath);
        }
    }

    // ── HTML ─────────────────────────────────────────────────────────────────

    /**
     * 使用 Jsoup 解析 HTML 文件：
     * 1. 提取页面元数据（title、description、keywords、author）
     * 2. 移除噪音节点（script、style、nav、header、footer 等）
     * 3. 将表格转换为 Markdown 风格的文本
     * 4. 按 h1/h2/h3 标题切分为多个 Section Document
     */
    private List<Document> parseHtml(String filePath) {
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(new File(filePath), null);

            String pageTitle = doc.title();
            String description = doc.select("meta[name=description]").attr("content");
            String keywords = doc.select("meta[name=keywords]").attr("content");
            String author = doc.select("meta[name=author]").attr("content");

            // 移除噪音标签
            doc.select("script, style, noscript, nav, header, footer, aside, form, button, iframe, [hidden]").remove();

            Element body = doc.body() != null ? doc.body() : doc.root();

            // 表格转文本，避免被直接丢弃
            convertTablesToText(body);

            List<Document> sections = splitByHeadings(body, filePath, pageTitle, description, keywords, author);
            if (!sections.isEmpty()) return sections;

            // 无标题结构时，整体作为一个文档
            String text = extractText(body).strip();
            if (text.isBlank()) return parseTika(filePath);
            return List.of(new Document(text, buildMeta(filePath, pageTitle, description, keywords, author, null, 0)));

        } catch (Exception e) {
            log.warn("HTML parsing with Jsoup failed, falling back to Tika for {}: {}", filePath, e.getMessage());
            return parseTika(filePath);
        }
    }

    /**
     * 将 {@code <table>} 元素转换为 Markdown 风格的文本节点，保留内容结构。
     */
    private void convertTablesToText(Element root) {
        for (Element table : root.select("table")) {
            StringBuilder sb = new StringBuilder("\n");
            boolean headerDone = false;
            for (Element row : table.select("tr")) {
                Elements cells = row.select("th, td");
                if (cells.isEmpty()) continue;
                sb.append("| ");
                cells.forEach(c -> sb.append(c.text().replace("|", "｜")).append(" | "));
                sb.append("\n");
                // 在表头行后插入分隔线
                if (!headerDone && row.select("th").first() != null) {
                    sb.append("| ");
                    cells.forEach(c -> sb.append("--- | "));
                    sb.append("\n");
                    headerDone = true;
                }
            }
            table.replaceWith(new TextNode(sb.toString()));
        }
    }

    /**
     * 按 h1/h2/h3 标题将 body 切分为多个 Section，每个 Section 对应一个 Document。
     * 标题之前的内容（引言）单独作为第 0 个 Section 处理。
     */
    private List<Document> splitByHeadings(Element body, String filePath,
                                            String pageTitle, String description,
                                            String keywords, String author) {
        // 仅在 h1/h2/h3 级别切分，h4-h6 保留在正文中
        Elements headings = body.select("h1, h2, h3");
        if (headings.isEmpty()) return Collections.emptyList();

        List<Document> results = new ArrayList<>();
        List<Node> allNodes = body.childNodes();

        String currentHeading = pageTitle;
        StringBuilder currentContent = new StringBuilder();
        int sectionIndex = 0;
        boolean inSection = false;

        for (Node node : allNodes) {
            if (node instanceof Element el && HEADING_TAGS.contains(el.tagName())
                    && (el.tagName().equals("h1") || el.tagName().equals("h2") || el.tagName().equals("h3"))) {

                // 保存前一个 Section
                String text = currentContent.toString().strip();
                if (!text.isBlank()) {
                    results.add(new Document(text,
                            buildMeta(filePath, pageTitle, description, keywords, author, currentHeading, sectionIndex)));
                    sectionIndex++;
                }

                currentHeading = el.text().strip();
                currentContent = new StringBuilder();
                currentContent.append("## ").append(currentHeading).append("\n\n");
                inSection = true;

            } else {
                String text = nodeToText(node).strip();
                if (!text.isBlank()) {
                    currentContent.append(text).append("\n\n");
                }
            }
        }

        // 最后一个 Section
        String remaining = currentContent.toString().strip();
        if (!remaining.isBlank()) {
            results.add(new Document(remaining,
                    buildMeta(filePath, pageTitle, description, keywords, author, currentHeading, sectionIndex)));
        }

        return results;
    }

    /**
     * 将单个节点转换为纯文本，处理块级元素换行和代码块缩进。
     */
    private String nodeToText(Node node) {
        if (node instanceof TextNode tn) return tn.getWholeText();
        if (!(node instanceof Element el)) return "";

        String tag = el.tagName();
        if (tag.equals("pre") || tag.equals("code")) {
            return "\n```\n" + el.wholeText() + "\n```\n";
        }
        if (tag.equals("br")) return "\n";
        if (tag.equals("hr")) return "\n---\n";

        StringBuilder sb = new StringBuilder();
        el.childNodes().forEach(child -> sb.append(nodeToText(child)));
        String inner = sb.toString();

        if (BLOCK_TAGS.contains(tag)) return "\n" + inner.strip() + "\n";
        if (HEADING_TAGS.contains(tag)) {
            int level = tag.charAt(1) - '0';
            return "\n" + "#".repeat(level) + " " + inner.strip() + "\n";
        }
        return inner;
    }

    /**
     * 提取 Element 下所有可见文本（用于无标题页面的 fallback）。
     */
    private String extractText(Element el) {
        StringBuilder sb = new StringBuilder();
        el.childNodes().forEach(node -> sb.append(nodeToText(node)));
        return sb.toString();
    }

    private Map<String, Object> buildMeta(String filePath, String pageTitle, String description,
                                           String keywords, String author,
                                           String sectionHeading, int sectionIndex) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", filePath);
        if (!pageTitle.isBlank()) meta.put("title", pageTitle);
        if (!description.isBlank()) meta.put("description", description);
        if (!keywords.isBlank()) meta.put("keywords", keywords);
        if (!author.isBlank()) meta.put("author", author);
        if (sectionHeading != null && !sectionHeading.isBlank()) meta.put("section", sectionHeading);
        meta.put("section_index", sectionIndex);
        return meta;
    }

    // ── Tika fallback ────────────────────────────────────────────────────────

    private List<Document> parseTika(String filePath) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
        return reader.get();
    }
}
