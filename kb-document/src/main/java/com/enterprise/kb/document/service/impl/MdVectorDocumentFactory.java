package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.model.MdChildChunk;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Markdown child chunk 向量文档构建器。
 */
@Component
class MdVectorDocumentFactory {

    Document from(MdChildChunk child) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", child.getDocumentId().toString());
        metadata.put("spaceId", child.getSpaceId().toString());
        metadata.put("parentId", child.getParentId().toString());
        metadata.put("section", child.getSection());
        metadata.put("seqInParent", child.getSeqInParent());
        metadata.put("contentType", child.getContentType());
        if (child.getAssetId() != null) {
            metadata.put("assetId", child.getAssetId().toString());
            metadata.put("assetUrl", child.getAssetUrl());
            metadata.put("assetTitle", child.getAssetTitle());
            metadata.put("objectKey", child.getAssetObjectKey());
        }
        String text = (child.getSection() == null || child.getSection().isBlank())
                ? child.getEmbedText()
                : child.getSection() + "\n\n" + child.getEmbedText();
        return new Document(child.getId().toString(), text, metadata);
    }
}
