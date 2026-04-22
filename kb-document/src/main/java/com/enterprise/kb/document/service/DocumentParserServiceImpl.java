package com.enterprise.kb.document.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DocumentParserServiceImpl implements DocumentParserService {

    @Override
    public List<Document> parse(String filePath, String mimeType) {
        log.debug("Parsing file {} with mimeType {}", filePath, mimeType);
        if ("application/pdf".equals(mimeType)) return parsePdf(filePath);
        return parseTika(filePath);
    }

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

    private List<Document> parseTika(String filePath) {
        TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(filePath));
        return reader.get();
    }
}
