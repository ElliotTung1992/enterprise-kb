package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsReadingItemMapper;
import com.enterprise.kb.ielts.model.IeltsReadingItem;
import com.enterprise.kb.ielts.service.IeltsReadingItemService;
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
public class IeltsReadingItemServiceImpl implements IeltsReadingItemService {

    private final IeltsReadingItemMapper itemMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsReadingItem> listItems(Integer difficulty, String trainingType, String questionType, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsReadingItem> list = itemMapper.findAll(difficulty, trainingType, questionType, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsReadingItem getById(UUID id) {
        return itemMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsReadingItem", id));
    }

    @Override
    @Transactional
    public IeltsReadingItem create(IeltsReadingItem item) {
        item.setId(UUID.randomUUID());
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        itemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional
    public IeltsReadingItem update(UUID id, IeltsReadingItem item) {
        IeltsReadingItem existing = getById(id);
        item.setId(existing.getId());
        item.setCreatedAt(existing.getCreatedAt());
        item.setUpdatedAt(Instant.now());
        itemMapper.update(item);
        return item;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        itemMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsReadingItem> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        items.forEach(i -> {
            if (i.getId() == null) i.setId(UUID.randomUUID());
            if (i.getCreatedAt() == null) i.setCreatedAt(now);
            if (i.getUpdatedAt() == null) i.setUpdatedAt(now);
        });
        return itemMapper.batchInsert(items);
    }
}
