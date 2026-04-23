package com.enterprise.kb.document.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 文档解析服务接口，将文件内容解析为 Spring AI Document 对象列表。
 */
public interface DocumentParserService {

    /**
     * Parse a file into a list of Spring AI Documents.
     *
     * @param filePath the path to the file
     * @param mimeType the MIME type of the file
     * @return list of parsed documents
     */
    List<Document> parse(String filePath, String mimeType);
}
