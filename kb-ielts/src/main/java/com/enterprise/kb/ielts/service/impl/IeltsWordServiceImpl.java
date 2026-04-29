package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsWordMapper;
import com.enterprise.kb.ielts.model.IeltsWord;
import com.enterprise.kb.ielts.service.IeltsWordService;
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
public class IeltsWordServiceImpl implements IeltsWordService {

    private final IeltsWordMapper wordMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsWord> listWords(Integer difficulty, String wordList, String topicTags, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsWord> list = wordMapper.findAll(difficulty, wordList, topicTags);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsWord getById(UUID id) {
        return wordMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsWord", id));
    }

    @Override
    @Transactional
    public IeltsWord create(IeltsWord word) {
        word.setId(UUID.randomUUID());
        Instant now = Instant.now();
        word.setCreatedAt(now);
        word.setUpdatedAt(now);
        wordMapper.insert(word);
        return word;
    }

    @Override
    @Transactional
    public IeltsWord update(UUID id, IeltsWord word) {
        IeltsWord existing = getById(id);
        word.setId(existing.getId());
        word.setCreatedAt(existing.getCreatedAt());
        word.setUpdatedAt(Instant.now());
        wordMapper.update(word);
        return word;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        wordMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsWord> words) {
        if (words == null || words.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        words.forEach(w -> {
            if (w.getId() == null) w.setId(UUID.randomUUID());
            if (w.getCreatedAt() == null) w.setCreatedAt(now);
            if (w.getUpdatedAt() == null) w.setUpdatedAt(now);
        });
        return wordMapper.batchInsert(words);
    }
}
