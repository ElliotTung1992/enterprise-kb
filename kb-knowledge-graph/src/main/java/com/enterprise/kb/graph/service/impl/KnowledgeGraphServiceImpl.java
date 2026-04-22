package com.enterprise.kb.graph.service.impl;

import com.enterprise.kb.graph.service.KnowledgeGraphService;
import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.mapper.DocumentMapper;
import com.enterprise.kb.document.mapper.DocumentRelationMapper;
import com.enterprise.kb.document.model.Document;
import com.enterprise.kb.graph.dto.GraphDto;
import com.enterprise.kb.graph.mapper.DocumentTagMapper;
import com.enterprise.kb.graph.mapper.TagMapper;
import com.enterprise.kb.graph.model.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private final DocumentMapper documentMapper;
    private final DocumentRelationMapper documentRelationMapper;
    private final DocumentTagMapper documentTagMapper;
    private final TagMapper tagMapper;

    @Override
    @Transactional(readOnly = true)
    public GraphDto buildGraphView(UUID spaceId) {
        List<Document> documents = documentMapper.findBySpaceId(spaceId, DocumentStatus.READY, null);
        Map<UUID, List<String>> docTagsMap = buildDocTagsMap(documents);

        List<GraphDto.GraphNode> nodes = documents.stream().map(d -> new GraphDto.GraphNode(
                d.getId(), d.getTitle(),
                docTagsMap.getOrDefault(d.getId(), List.of()),
                d.getChunkCount() != null ? d.getChunkCount() : 0)).toList();

        List<GraphDto.GraphEdge> edges = new ArrayList<>();
        for (Document doc : documents) {
            documentRelationMapper.findByDocumentId(doc.getId()).forEach(rel ->
                    edges.add(new GraphDto.GraphEdge(rel.getSourceDocId(), rel.getTargetDocId(),
                            rel.getRelationType().name(),
                            rel.getWeight() != null ? rel.getWeight().doubleValue() : 1.0)));
        }
        addImplicitTagEdges(documents, docTagsMap, edges);
        return new GraphDto(nodes, edges.stream().distinct().toList());
    }

    private Map<UUID, List<String>> buildDocTagsMap(List<Document> documents) {
        Map<UUID, List<String>> result = new HashMap<>();
        Map<UUID, String> tagNames = tagMapper.findAll().stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        for (Document doc : documents) {
            result.put(doc.getId(), documentTagMapper.findByDocumentId(doc.getId())
                    .stream().map(dt -> tagNames.getOrDefault(dt.getTagId(), "unknown")).toList());
        }
        return result;
    }

    private void addImplicitTagEdges(List<Document> documents,
                                     Map<UUID, List<String>> docTagsMap,
                                     List<GraphDto.GraphEdge> edges) {
        List<UUID> docIds = documents.stream().map(Document::getId).toList();
        for (int i = 0; i < docIds.size(); i++) {
            for (int j = i + 1; j < docIds.size(); j++) {
                UUID a = docIds.get(i), b = docIds.get(j);
                Set<String> tagsA = new HashSet<>(docTagsMap.getOrDefault(a, List.of()));
                tagsA.retainAll(new HashSet<>(docTagsMap.getOrDefault(b, List.of())));
                if (tagsA.size() >= 2) edges.add(new GraphDto.GraphEdge(a, b, "RELATED_TO", 0.5));
            }
        }
    }
}
