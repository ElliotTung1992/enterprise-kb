package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsExampleMapper;
import com.enterprise.kb.ielts.mapper.IeltsGrammarPointMapper;
import com.enterprise.kb.ielts.model.IeltsExample;
import com.enterprise.kb.ielts.model.IeltsGrammarPoint;
import com.enterprise.kb.ielts.service.IeltsGrammarPointService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IeltsGrammarPointServiceImpl implements IeltsGrammarPointService {

    private static final String CONTENT_TYPE = "GRAMMAR_POINT";

    private final IeltsGrammarPointMapper pointMapper;
    private final IeltsExampleMapper exampleMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsGrammarPoint> listPoints(Integer difficulty, String category, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsGrammarPoint> list = pointMapper.findAll(difficulty, category, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsGrammarPoint getById(UUID id) {
        IeltsGrammarPoint point = pointMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsGrammarPoint", id));
        point.setExamples(exampleMapper.findByContent(CONTENT_TYPE, id));
        return point;
    }

    @Override
    @Transactional
    public IeltsGrammarPoint create(IeltsGrammarPoint point) {
        point.setId(UUID.randomUUID());
        Instant now = Instant.now();
        point.setCreatedAt(now);
        point.setUpdatedAt(now);
        pointMapper.insert(point);
        saveExamples(point.getId(), point.getExamples());
        return point;
    }

    @Override
    @Transactional
    public IeltsGrammarPoint update(UUID id, IeltsGrammarPoint point) {
        IeltsGrammarPoint existing = pointMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsGrammarPoint", id));
        point.setId(existing.getId());
        point.setCreatedAt(existing.getCreatedAt());
        point.setUpdatedAt(Instant.now());
        pointMapper.update(point);
        saveExamples(id, point.getExamples());
        return point;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        pointMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("IeltsGrammarPoint", id));
        exampleMapper.deleteByContent(CONTENT_TYPE, id);
        pointMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsGrammarPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        points.forEach(p -> {
            if (p.getId() == null) p.setId(UUID.randomUUID());
            if (p.getCreatedAt() == null) p.setCreatedAt(now);
            if (p.getUpdatedAt() == null) p.setUpdatedAt(now);
        });
        return pointMapper.batchInsert(points);
    }

    private void saveExamples(UUID contentId, List<IeltsExample> examples) {
        exampleMapper.deleteByContent(CONTENT_TYPE, contentId);
        if (examples == null || examples.isEmpty()) return;
        Instant now = Instant.now();
        for (int i = 0; i < examples.size(); i++) {
            IeltsExample ex = examples.get(i);
            if (ex.getId() == null) ex.setId(UUID.randomUUID());
            ex.setContentType(CONTENT_TYPE);
            ex.setContentId(contentId);
            ex.setSortOrder(i);
            if (ex.getCreatedAt() == null) ex.setCreatedAt(now);
        }
        exampleMapper.batchInsert(examples);
    }
}
