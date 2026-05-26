package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.markdown.MarkdownStructureIngestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownStructureIngestionServiceImplTest {

    private MarkdownStructureIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarkdownStructureIngestionServiceImpl();
        ReflectionTestUtils.setField(service, "maxTokens", 80);
        ReflectionTestUtils.setField(service, "minTokens", 10);
    }

    @Test
    void parseSplitsByH1ToH3AndPrefixesBreadcrumbForVectorText(@TempDir Path tempDir) throws IOException {
        Path file = write(tempDir, """
                # 手册

                开篇说明。

                ## 安装

                Windows 安装步骤。

                ### 配置

                修改配置文件。
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        assertThat(result.parents()).extracting("section")
                .contains("手册", "手册 > 安装", "手册 > 安装 > 配置");
        assertThat(result.vectorDocuments().getLast().getText())
                .startsWith("手册 > 安装 > 配置")
                .contains("修改配置文件");
    }

    @Test
    void parseLinearizesTableForEmbedTextButKeepsOriginalInParent(@TempDir Path tempDir) throws IOException {
        Path file = write(tempDir, """
                # 价格

                | 型号 | 价格 | 存储 |
                | --- | --- | --- |
                | iPhone 15 | 5999 | 128G |
                | iPhone 15 Pro | 7999 | 256G |
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        assertThat(result.children()).extracting("embedText")
                .anySatisfy(text -> assertThat((String) text).contains("型号: iPhone 15").contains("价格: 5999"));
        assertThat(result.parents().getFirst().getContent()).contains("| 型号 | 价格 | 存储 |");
    }

    @Test
    void parseDoesNotTreatInlinePipeParagraphAsTable(@TempDir Path tempDir) throws IOException {
        Path file = write(tempDir, """
                # 说明

                Java 中可以用 a | b 做位运算，状态也可能写成成功 | 失败 | 处理中。
                下一句仍然属于普通段落。
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        assertThat(result.children()).extracting("embedText")
                .anySatisfy(text -> assertThat((String) text)
                        .contains("a | b")
                        .doesNotContain("Java 中可以用 a:"));
    }

    @Test
    void parseSplitsLargeTableByLinearizedRows(@TempDir Path tempDir) throws IOException {
        ReflectionTestUtils.setField(service, "maxTokens", 35);
        Path file = write(tempDir, """
                # 报价

                | 型号 | 价格 | 存储 |
                | --- | --- | --- |
                | iPhone 15 | 5999 | 128G |
                | iPhone 15 Pro | 7999 | 256G |
                | iPhone 15 Pro Max | 9999 | 512G |
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        assertThat(result.children()).hasSizeGreaterThan(1);
        assertThat(result.children()).extracting("embedText")
                .anySatisfy(text -> assertThat((String) text).contains("型号: iPhone 15 Pro Max"));
    }

    @Test
    void parseKeepsCodeFenceAsAtomicBlockEvenWhenItContainsBlankLine(@TempDir Path tempDir) throws IOException {
        Path file = write(tempDir, """
                # 示例

                ```java
                class Demo {

                    void run() {}
                }
                ```

                后续说明。
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        assertThat(result.children()).extracting("embedText")
                .anySatisfy(text -> assertThat((String) text).contains("class Demo").contains("void run"));
    }

    @Test
    void parseSplitsOversizedListByItems(@TempDir Path tempDir) throws IOException {
        Path file = write(tempDir, """
                # 清单

                - 第一项包含很多很多很多很多很多很多很多很多很多很多很多很多内容
                - 第二项包含很多很多很多很多很多很多很多很多很多很多很多很多内容
                - 第三项包含很多很多很多很多很多很多很多很多很多很多很多很多内容
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        assertThat(result.children()).hasSizeGreaterThan(1);
        assertThat(result.children()).extracting("embedText")
                .anySatisfy(text -> assertThat((String) text).contains("第一项"));
    }

    @Test
    void parsePacksSmallBlocksAfterFlushedSiblingInsteadOfMergingBack(@TempDir Path tempDir) throws IOException {
        // 一个已达标并被 flush 的大段落，后跟若干小段落：小段落应贪心打包成独立 child，
        // 而非被逐个吸回前一兄弟、把它撑过 max。
        ReflectionTestUtils.setField(service, "maxTokens", 40);
        ReflectionTestUtils.setField(service, "minTokens", 8);
        Path file = write(tempDir, """
                # 标题

                锚段标识内容比较长用来把第一个子块尽量撑到接近最大上限的位置附近停下来

                标记甲短句内容

                标记乙短句内容

                标记丙短句内容
                """);

        MarkdownStructureIngestionResult result = service.parse(UUID.randomUUID(), UUID.randomUUID(), file.toString());

        // 大段落独立成块，三个小段落打包进同一个后续 child
        assertThat(result.children()).hasSize(2);
        assertThat(result.children().getFirst().getEmbedText())
                .contains("锚段")
                .doesNotContain("甲");
        assertThat(result.children().getLast().getEmbedText())
                .contains("甲").contains("乙").contains("丙")
                .doesNotContain("锚段");
    }

    private Path write(Path tempDir, String markdown) throws IOException {
        Path file = tempDir.resolve("doc.md");
        Files.writeString(file, markdown);
        return file;
    }
}
