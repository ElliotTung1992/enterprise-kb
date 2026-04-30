package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsExampleMapper;
import com.enterprise.kb.ielts.mapper.IeltsPhraseMapper;
import com.enterprise.kb.ielts.model.IeltsExample;
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

    private static final String CONTENT_TYPE = "PHRASE";

    private final IeltsPhraseMapper phraseMapper;
    private final IeltsExampleMapper exampleMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsPhrase> listPhrases(Integer difficulty, String category, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsPhrase> list = phraseMapper.findAll(difficulty, category, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsPhrase getById(UUID id) {
        IeltsPhrase phrase = phraseMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsPhrase", id));
        phrase.setExamples(exampleMapper.findByContent(CONTENT_TYPE, id));
        return phrase;
    }

    @Override
    @Transactional
    public IeltsPhrase create(IeltsPhrase phrase) {
        phrase.setId(UUID.randomUUID());
        Instant now = Instant.now();
        phrase.setCreatedAt(now);
        phrase.setUpdatedAt(now);
        phraseMapper.insert(phrase);
        saveExamples(phrase.getId(), phrase.getExamples());
        return phrase;
    }

    @Override
    @Transactional
    public IeltsPhrase update(UUID id, IeltsPhrase phrase) {
        IeltsPhrase existing = phraseMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsPhrase", id));
        phrase.setId(existing.getId());
        phrase.setCreatedAt(existing.getCreatedAt());
        phrase.setUpdatedAt(Instant.now());
        phraseMapper.update(phrase);
        saveExamples(id, phrase.getExamples());
        return phrase;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        phraseMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("IeltsPhrase", id));
        exampleMapper.deleteByContent(CONTENT_TYPE, id);
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
