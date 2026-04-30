package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsGrammarExerciseMapper;
import com.enterprise.kb.ielts.model.IeltsGrammarExercise;
import com.enterprise.kb.ielts.service.IeltsGrammarExerciseService;
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
public class IeltsGrammarExerciseServiceImpl implements IeltsGrammarExerciseService {

    private final IeltsGrammarExerciseMapper exerciseMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsGrammarExercise> listExercises(Integer difficulty, String questionType, UUID grammarPointId, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsGrammarExercise> list = exerciseMapper.findAll(difficulty, questionType, grammarPointId, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsGrammarExercise getById(UUID id) {
        return exerciseMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsGrammarExercise", id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IeltsGrammarExercise> listByGrammarPoint(UUID grammarPointId) {
        return exerciseMapper.findByGrammarPointId(grammarPointId);
    }

    @Override
    @Transactional
    public IeltsGrammarExercise create(IeltsGrammarExercise exercise) {
        exercise.setId(UUID.randomUUID());
        Instant now = Instant.now();
        exercise.setCreatedAt(now);
        exercise.setUpdatedAt(now);
        exerciseMapper.insert(exercise);
        return exercise;
    }

    @Override
    @Transactional
    public IeltsGrammarExercise update(UUID id, IeltsGrammarExercise exercise) {
        IeltsGrammarExercise existing = getById(id);
        exercise.setId(existing.getId());
        exercise.setCreatedAt(existing.getCreatedAt());
        exercise.setUpdatedAt(Instant.now());
        exerciseMapper.update(exercise);
        return exercise;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        exerciseMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsGrammarExercise> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        exercises.forEach(e -> {
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getCreatedAt() == null) e.setCreatedAt(now);
            if (e.getUpdatedAt() == null) e.setUpdatedAt(now);
        });
        return exerciseMapper.batchInsert(exercises);
    }
}
