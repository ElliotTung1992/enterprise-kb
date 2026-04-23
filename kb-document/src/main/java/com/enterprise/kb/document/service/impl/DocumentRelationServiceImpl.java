package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.DocumentRelationService;
import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.document.dto.AddRelationRequest;
import com.enterprise.kb.document.dto.DocumentRelationDto;
import com.enterprise.kb.document.mapper.DocumentRelationMapper;
import com.enterprise.kb.document.model.DocumentRelation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 文档关系服务实现，提供文档间关系的创建、查询与删除功能。
 * <p>关系类型包括 REFERENCES、RELATED_TO 等，关系可由用户手动创建或自动检测生成。
 * 同一源文档到同一目标文档的同一关系类型不允许重复。</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentRelationServiceImpl implements DocumentRelationService {

    private final DocumentRelationMapper relationMapper;

    /**
     * 添加文档关系。
     *
     * @param sourceDocId 源文档 UUID
     * @param req         关系请求
     * @param userId      创建用户 UUID
     * @return 关系 DTO
     */
    @Override
    @Transactional
    public DocumentRelationDto addRelation(UUID sourceDocId, AddRelationRequest req, UUID userId) {
        if (relationMapper.existsBySourceDocIdAndTargetDocIdAndRelationType(
                sourceDocId, req.targetDocId(), req.relationType()))
            throw new InvalidRequestException("Relation already exists");
        DocumentRelation rel = new DocumentRelation();
        rel.setId(UUID.randomUUID());
        rel.setSourceDocId(sourceDocId);
        rel.setTargetDocId(req.targetDocId());
        rel.setRelationType(req.relationType());
        rel.setCreatedBy(userId);
        relationMapper.insert(rel);
        return toDto(rel);
    }

    /**
     * 获取文档的所有关系。
     *
     * @param documentId 文档 UUID
     * @return 关系 DTO 列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<DocumentRelationDto> getRelations(UUID documentId) {
        return relationMapper.findByDocumentId(documentId).stream().map(this::toDto).toList();
    }

    /**
     * 删除关系。
     *
     * @param relationId 关系 UUID
     */
    @Override
    @Transactional
    public void deleteRelation(UUID relationId) {
        relationMapper.deleteById(relationId);
    }

    private DocumentRelationDto toDto(DocumentRelation r) {
        return new DocumentRelationDto(r.getId(), r.getSourceDocId(), r.getTargetDocId(),
                r.getRelationType(), r.getWeight(), r.isAutoDetected(), r.getCreatedAt());
    }
}
