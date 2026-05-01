package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceExistException;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsExampleMapper;
import com.enterprise.kb.ielts.mapper.IeltsWordMapper;
import com.enterprise.kb.ielts.model.IeltsExample;
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

    private static final String CONTENT_TYPE = "WORD";

    private final IeltsWordMapper wordMapper;
    private final IeltsExampleMapper exampleMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsWord> listWords(Integer difficulty, String wordList, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsWord> list = wordMapper.findAll(difficulty, wordList, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsWord getById(UUID id) {
        IeltsWord word = wordMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsWord", id));
        word.setExamples(exampleMapper.findByContent(CONTENT_TYPE, id));
        return word;
    }

    @Override
    @Transactional
    public IeltsWord create(IeltsWord word) {

        if(wordMapper.findByWord(word.getWord()).isPresent()){
            throw new ResourceExistException(CONTENT_TYPE, "word_name", word.getWord());
        }

        word.setId(UUID.randomUUID());
        Instant now = Instant.now();
        word.setCreatedAt(now);
        word.setUpdatedAt(now);
        wordMapper.insert(word);
        saveExamples(word.getId(), word.getExamples());
        return word;
    }

    @Override
    @Transactional
    public IeltsWord update(UUID id, IeltsWord word) {
        IeltsWord existing = wordMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsWord", id));
        word.setId(existing.getId());
        word.setCreatedAt(existing.getCreatedAt());
        word.setUpdatedAt(Instant.now());
        wordMapper.update(word);
        saveExamples(id, word.getExamples());
        return word;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        wordMapper.findById(id).orElseThrow(() -> new ResourceNotFoundException("IeltsWord", id));
        exampleMapper.deleteByContent(CONTENT_TYPE, id);
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
