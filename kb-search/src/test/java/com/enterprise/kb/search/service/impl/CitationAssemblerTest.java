package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.search.dto.Citation;
import com.enterprise.kb.search.dto.SearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationAssemblerTest {

    @Test
    void sameAssetKeepsCaptionInsteadOfReference() {
        UUID documentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        SearchHit reference = hit("ref", documentId, "IMAGE_REFERENCE", assetId, "图片引用", 0.95);
        SearchHit caption = hit("caption", documentId, "IMAGE_CAPTION", assetId, "图片说明", 0.70);

        List<Citation> citations = CitationAssembler.fromHits(List.of(reference, caption));

        assertThat(citations).hasSize(1);
        assertThat(citations.getFirst().chunkId()).isEqualTo("caption");
        assertThat(citations.getFirst().contentType()).isEqualTo("IMAGE_CAPTION");
        assertThat(citations.getFirst().assetUrl()).isEqualTo("http://localhost:9000/kb-assets/asset.png");
        assertThat(citations.getFirst().assetTitle()).isEqualTo("图片说明");
        assertThat(citations.getFirst().citationNumber()).isEqualTo(1);
    }

    @Test
    void nonAssetChunksAreKeptAndRenumbered() {
        UUID documentId = UUID.randomUUID();
        SearchHit first = hit("chunk-1", documentId, "TEXT", null, "正文一", 0.8);
        SearchHit second = hit("chunk-2", documentId, "TEXT", null, "正文二", 0.7);

        List<Citation> citations = CitationAssembler.fromHits(List.of(first, second));

        assertThat(citations).extracting(Citation::chunkId).containsExactly("chunk-1", "chunk-2");
        assertThat(citations).extracting(Citation::citationNumber).containsExactly(1, 2);
    }

    @Test
    void sameAssetWithSameContentTypeKeepsHigherScore() {
        UUID documentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        SearchHit lowScore = hit("caption-low", documentId, "IMAGE_CAPTION", assetId, "低分说明", 0.5);
        SearchHit highScore = hit("caption-high", documentId, "IMAGE_CAPTION", assetId, "高分说明", 0.9);

        List<Citation> citations = CitationAssembler.fromHits(List.of(lowScore, highScore));

        assertThat(citations).hasSize(1);
        assertThat(citations.getFirst().chunkId()).isEqualTo("caption-high");
        assertThat(citations.getFirst().score()).isEqualTo(0.9);
    }

    private SearchHit hit(String chunkId, UUID documentId, String contentType,
                          UUID assetId, String excerpt, double score) {
        return new SearchHit(chunkId, documentId, "文档", excerpt, null, score,
                "text/markdown", contentType, assetId,
                assetId == null ? null : "http://localhost:9000/kb-assets/asset.png",
                assetId == null ? null : excerpt,
                "章节", 1);
    }
}
