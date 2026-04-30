package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsListeningItemMapper;
import com.enterprise.kb.ielts.model.IeltsListeningItem;
import com.enterprise.kb.ielts.service.IeltsListeningItemService;
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
public class IeltsListeningItemServiceImpl implements IeltsListeningItemService {

    private final IeltsListeningItemMapper itemMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsListeningItem> listItems(Integer difficulty, Integer section, String questionType, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsListeningItem> list = itemMapper.findAll(difficulty, section, questionType, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsListeningItem getById(UUID id) {
        return itemMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsListeningItem", id));
    }

    @Override
    @Transactional
    public IeltsListeningItem create(IeltsListeningItem item) {
        item.setId(UUID.randomUUID());
        Instant now = Instant.now();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        itemMapper.insert(item);
        return item;
    }

    @Override
    @Transactional
    public IeltsListeningItem update(UUID id, IeltsListeningItem item) {
        IeltsListeningItem existing = getById(id);
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
    public int batchImport(List<IeltsListeningItem> items) {
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
