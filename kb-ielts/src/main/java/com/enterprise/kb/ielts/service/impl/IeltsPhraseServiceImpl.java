package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsPhraseMapper;
import com.enterprise.kb.ielts.model.IeltsPhrase;
import com.enterprise.kb.ielts.service.IeltsPhraseService;
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
public class IeltsPhraseServiceImpl implements IeltsPhraseService {

    private final IeltsPhraseMapper phraseMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsPhrase> listPhrases(Integer difficulty, String category, String topicTags, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsPhrase> list = phraseMapper.findAll(difficulty, category, topicTags);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsPhrase getById(UUID id) {
        return phraseMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsPhrase", id));
    }

    @Override
    @Transactional
    public IeltsPhrase create(IeltsPhrase phrase) {
        phrase.setId(UUID.randomUUID());
        Instant now = Instant.now();
        phrase.setCreatedAt(now);
        phrase.setUpdatedAt(now);
        phraseMapper.insert(phrase);
        return phrase;
    }

    @Override
    @Transactional
    public IeltsPhrase update(UUID id, IeltsPhrase phrase) {
        IeltsPhrase existing = getById(id);
        phrase.setId(existing.getId());
        phrase.setCreatedAt(existing.getCreatedAt());
        phrase.setUpdatedAt(Instant.now());
        phraseMapper.update(phrase);
        return phrase;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        phraseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsPhrase> phrases) {
        if (phrases == null || phrases.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        phrases.forEach(p -> {
            if (p.getId() == null) p.setId(UUID.randomUUID());
            if (p.getCreatedAt() == null) p.setCreatedAt(now);
            if (p.getUpdatedAt() == null) p.setUpdatedAt(now);
        });
        return phraseMapper.batchInsert(phrases);
    }
}
