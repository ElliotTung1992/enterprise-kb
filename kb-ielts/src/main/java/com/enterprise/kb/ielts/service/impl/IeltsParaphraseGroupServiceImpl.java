package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsParaphraseGroupMapper;
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

    private final IeltsParaphraseGroupMapper groupMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsParaphraseGroup> listGroups(Integer difficulty, String topicTags, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsParaphraseGroup> list = groupMapper.findAll(difficulty, topicTags);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsParaphraseGroup getById(UUID id) {
        return groupMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsParaphraseGroup", id));
    }

    @Override
    @Transactional
    public IeltsParaphraseGroup create(IeltsParaphraseGroup group) {
        group.setId(UUID.randomUUID());
        Instant now = Instant.now();
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        groupMapper.insert(group);
        return group;
    }

    @Override
    @Transactional
    public IeltsParaphraseGroup update(UUID id, IeltsParaphraseGroup group) {
        IeltsParaphraseGroup existing = getById(id);
        group.setId(existing.getId());
        group.setCreatedAt(existing.getCreatedAt());
        group.setUpdatedAt(Instant.now());
        groupMapper.update(group);
        return group;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
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
}
