package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsExampleMapper;
import com.enterprise.kb.ielts.mapper.IeltsParaphraseGroupMapper;
import com.enterprise.kb.ielts.model.IeltsExample;
import com.enterprise.kb.ielts.model.IeltsParaphraseGroup;
import com.enterprise.kb.ielts.service.IeltsParaphraseGroupService;
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
public class IeltsParaphraseGroupServiceImpl implements IeltsParaphraseGroupService {

    private static final String CONTENT_TYPE = "PARAPHRASE";

    private final IeltsParaphraseGroupMapper groupMapper;
    private final IeltsExampleMapper exampleMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsParaphraseGroup> listGroups(Integer difficulty, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsParaphraseGroup> list = groupMapper.findAll(difficulty, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsParaphraseGroup getById(UUID id) {
        IeltsParaphraseGroup group = groupMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsParaphraseGroup", id));
        group.setExamples(exampleMapper.findByContent(CONTENT_TYPE, id));
        return group;
    }

    @Override
    @Transactional
    public IeltsParaphraseGroup create(IeltsParaphraseGroup group) {
        group.setId(UUID.randomUUID());
        Instant now = Instant.now();
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        groupMapper.insert(group);
        saveExamples(group.getId(), group.getExamples());
        return group;
    }

    @Override
    @Transactional
    public IeltsParaphraseGroup update(UUID id, IeltsParaphraseGroup group) {
        IeltsParaphraseGroup existing = groupMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsParaphraseGroup", id));
        group.setId(existing.getId());
        group.setCreatedAt(existing.getCreatedAt());
        group.setUpdatedAt(Instant.now());
        groupMapper.update(group);
        saveExamples(id, group.getExamples());
        return group;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        groupMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("IeltsParaphraseGroup", id));
        exampleMapper.deleteByContent(CONTENT_TYPE, id);
        groupMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsParaphraseGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        groups.forEach(g -> {
            if (g.getId() == null) g.setId(UUID.randomUUID());
            if (g.getCreatedAt() == null) g.setCreatedAt(now);
            if (g.getUpdatedAt() == null) g.setUpdatedAt(now);
        });
        return groupMapper.batchInsert(groups);
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
