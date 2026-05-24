package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.constants.AssetType;
import com.enterprise.kb.common.constants.DiagramType;
import com.enterprise.kb.document.mapper.DocumentAssetMapper;
import com.enterprise.kb.document.model.DocumentAsset;
import com.enterprise.kb.document.pipeline.IngestionContext;
import com.enterprise.kb.document.service.DiagramRenderService;
import com.enterprise.kb.document.service.DocumentObjectStorageService;
import com.enterprise.kb.document.service.MarkdownVisualIngestionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkdownVisualIngestionServiceImplTest {

    @Mock DocumentAssetMapper assetMapper;
    @Mock DocumentObjectStorageService objectStorageService;
    @Mock DiagramRenderService diagramRenderService;

    private MarkdownVisualIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarkdownVisualIngestionServiceImpl(assetMapper, objectStorageService, diagramRenderService);
        ReflectionTestUtils.setField(service, "maxFileCount", 20);
        ReflectionTestUtils.setField(service, "maxExtractedSizeMb", 10L);
        ReflectionTestUtils.setField(service, "maxImageCount", 10);
        ReflectionTestUtils.setField(service, "maxImageSizeMb", 5L);
    }

    @Test
    void extractParsesImagesMissingImagesMermaidAndPlantUml(@TempDir Path tempDir)
            throws IOException, URISyntaxException {
        Path zip = tempDir.resolve("visual-rag.zip");
        zipFixture(zip);
        UUID documentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        when(objectStorageService.uploadFile(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(diagramRenderService.render(any(), any(), any())).thenReturn(Optional.empty());

        MarkdownVisualIngestionResult result = service.extract(IngestionContext.builder()
                .documentId(documentId)
                .spaceId(spaceId)
                .filePath(zip.toString())
                .mimeType("application/zip")
                .build());

        assertThat(result.mainMarkdownPath()).isEqualTo("README.md");
        assertThat(result.sourceZipObjectKey()).contains(documentId.toString()).contains("source");
        assertThat(result.textDocuments())
                .extracting(org.springframework.ai.document.Document::getText)
                .anySatisfy(text -> assertThat(text).contains("架构图").doesNotContain("missing.png"));
        assertThat(result.assets()).hasSize(3);
        assertThat(result.assets()).extracting(DocumentAsset::getAssetType)
                .containsExactly(AssetType.IMAGE, AssetType.DIAGRAM, AssetType.DIAGRAM);
        assertThat(result.assets()).extracting(DocumentAsset::getDiagramType)
                .contains(null, DiagramType.MERMAID, DiagramType.PLANTUML);
        assertThat(result.assets().getFirst().getObjectKey()).contains("assets");

        verify(objectStorageService).uploadFile(any(), any(), eq("application/zip"));
        verify(objectStorageService).uploadFile(any(), any(), eq("image/svg+xml"));
    }

    @Test
    void deleteByDocumentIdRemovesAssetObjectsBeforeDeletingRows() {
        UUID documentId = UUID.randomUUID();
        DocumentAsset asset = new DocumentAsset();
        asset.setObjectKey("documents/space/doc/assets/image.png");
        asset.setThumbnailObjectKey("documents/space/doc/assets/thumb.png");
        when(assetMapper.findByDocumentId(documentId)).thenReturn(List.of(asset));
        doAnswer(invocation -> {
            if (asset.getThumbnailObjectKey().equals(invocation.getArgument(0))) {
                throw new IllegalStateException("minio unavailable");
            }
            return null;
        }).when(objectStorageService).deleteFile(any());

        service.deleteByDocumentId(documentId);

        verify(objectStorageService).deleteFile(asset.getObjectKey());
        verify(objectStorageService).deleteFile(asset.getThumbnailObjectKey());
        verify(assetMapper).deleteByDocumentId(documentId);
    }

    private void zipFixture(Path zip) throws IOException, URISyntaxException {
        Path root = Path.of(Objects.requireNonNull(getClass().getResource("/markdown-visual-rag-l2")).toURI());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip));
             var paths = Files.walk(root)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path file : files) {
                String entryName = root.relativize(file).toString();
                out.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, out);
                out.closeEntry();
            }
        }
    }
}
