package com.enterprise.kb.graph.service.impl;

import com.enterprise.kb.graph.service.AutoTaggingService;
import com.enterprise.kb.graph.mapper.DocumentTagMapper;
import com.enterprise.kb.graph.mapper.TagMapper;
import com.enterprise.kb.graph.model.DocumentTag;
import com.enterprise.kb.graph.model.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoTaggingServiceImpl implements AutoTaggingService {

    private final ChatClient chatClient;
    private final TagMapper tagMapper;
    private final DocumentTagMapper documentTagMapper;

    public record TagSuggestion(String slug, double confidence) {}

    @Override
    @Async("ingestionExecutor")
    @Transactional
    public void suggestAndApplyTags(UUID documentId, UUID spaceId, String documentTitle, String excerpt) {
        List<Tag> availableTags = tagMapper.findBySpaceIdAndDeletedAtIsNullOrderBySortOrderAscNameAsc(spaceId);
        if (availableTags.isEmpty()) return;
        String tagSlugs = availableTags.stream().map(Tag::getSlug).reduce((a, b) -> a + ", " + b).orElse("");
        String truncatedExcerpt = excerpt.length() > 2000 ? excerpt.substring(0, 2000) : excerpt;
        String prompt = """
                You are a document classifier. Return a JSON array of matching tag slugs with confidence scores (0.0-1.0).
                Only return tags from the provided list. Return at most 5 tags. Return ONLY valid JSON.
                Available tags: %s
                Document title: %s
                Document excerpt: %s
                Return format: [{"slug": "...", "confidence": 0.95}]"""
                .formatted(tagSlugs, documentTitle, truncatedExcerpt);
        try {
            BeanOutputConverter<List<TagSuggestion>> converter =
                    new BeanOutputConverter<>(new ParameterizedTypeReference<List<TagSuggestion>>() {});
            List<TagSuggestion> suggestions = converter.convert(
                    chatClient.prompt().user(prompt).call().content());
            if (suggestions == null) return;
            for (TagSuggestion s : suggestions) {
                if (s.confidence() < 0.6) continue;
                availableTags.stream().filter(t -> t.getSlug().equals(s.slug())).findFirst()
                        .ifPresent(tag -> {
                            if (!documentTagMapper.existsByDocumentIdAndTagId(documentId, tag.getId())) {
                                DocumentTag dt = new DocumentTag();
                                dt.setId(UUID.randomUUID());
                                dt.setDocumentId(documentId);
                                dt.setTagId(tag.getId());
                                dt.setAutoTagged(true);
                                dt.setConfidence(BigDecimal.valueOf(s.confidence()));
                                documentTagMapper.insert(dt);
                            }
                        });
            }
            log.info("Auto-tagged document {} with {} suggestions", documentId, suggestions.size());
        } catch (Exception e) {
            log.warn("Auto-tagging failed for document {}: {}", documentId, e.getMessage());
        }
    }
}
