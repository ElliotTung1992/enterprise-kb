package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsSpeakingTopicMapper;
import com.enterprise.kb.ielts.model.IeltsSpeakingTopic;
import com.enterprise.kb.ielts.service.IeltsSpeakingTopicService;
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
public class IeltsSpeakingTopicServiceImpl implements IeltsSpeakingTopicService {

    private final IeltsSpeakingTopicMapper topicMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsSpeakingTopic> listTopics(Integer difficulty, Integer part, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsSpeakingTopic> list = topicMapper.findAll(difficulty, part, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsSpeakingTopic getById(UUID id) {
        return topicMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsSpeakingTopic", id));
    }

    @Override
    @Transactional
    public IeltsSpeakingTopic create(IeltsSpeakingTopic topic) {
        topic.setId(UUID.randomUUID());
        Instant now = Instant.now();
        topic.setCreatedAt(now);
        topic.setUpdatedAt(now);
        topicMapper.insert(topic);
        return topic;
    }

    @Override
    @Transactional
    public IeltsSpeakingTopic update(UUID id, IeltsSpeakingTopic topic) {
        IeltsSpeakingTopic existing = getById(id);
        topic.setId(existing.getId());
        topic.setCreatedAt(existing.getCreatedAt());
        topic.setUpdatedAt(Instant.now());
        topicMapper.update(topic);
        return topic;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        topicMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsSpeakingTopic> topics) {
        if (topics == null || topics.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        topics.forEach(t -> {
            if (t.getId() == null) t.setId(UUID.randomUUID());
            if (t.getCreatedAt() == null) t.setCreatedAt(now);
            if (t.getUpdatedAt() == null) t.setUpdatedAt(now);
        });
        return topicMapper.batchInsert(topics);
    }
}
