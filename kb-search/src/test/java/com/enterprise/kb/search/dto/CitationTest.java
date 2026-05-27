package com.enterprise.kb.search.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CitationTest {

    @Test
    void citationCarriesAssetLevelFields() {
        UUID documentId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();

        Citation citation = new Citation(
                1, "chunk-1", documentId, "文档", "图片说明", null, 0.91,
                "IMAGE_CAPTION", assetId, null, null, "图片章节", 5);

        assertThat(citation.assetId()).isEqualTo(assetId);
        assertThat(citation.contentType()).isEqualTo("IMAGE_CAPTION");
        assertThat(citation.section()).isEqualTo("图片章节");
        assertThat(citation.anchorChunkIndex()).isEqualTo(5);
    }
}
