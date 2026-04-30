package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsWritingTaskMapper;
import com.enterprise.kb.ielts.model.IeltsWritingTask;
import com.enterprise.kb.ielts.service.IeltsWritingTaskService;
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
public class IeltsWritingTaskServiceImpl implements IeltsWritingTaskService {

    private final IeltsWritingTaskMapper taskMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsWritingTask> listTasks(Integer difficulty, Integer taskNumber, String trainingType, String topicTags, String studyStatus, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsWritingTask> list = taskMapper.findAll(difficulty, taskNumber, trainingType, topicTags, studyStatus);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsWritingTask getById(UUID id) {
        return taskMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsWritingTask", id));
    }

    @Override
    @Transactional
    public IeltsWritingTask create(IeltsWritingTask task) {
        task.setId(UUID.randomUUID());
        Instant now = Instant.now();
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    @Override
    @Transactional
    public IeltsWritingTask update(UUID id, IeltsWritingTask task) {
        IeltsWritingTask existing = getById(id);
        task.setId(existing.getId());
        task.setCreatedAt(existing.getCreatedAt());
        task.setUpdatedAt(Instant.now());
        taskMapper.update(task);
        return task;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        taskMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsWritingTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        tasks.forEach(t -> {
            if (t.getId() == null) t.setId(UUID.randomUUID());
            if (t.getCreatedAt() == null) t.setCreatedAt(now);
            if (t.getUpdatedAt() == null) t.setUpdatedAt(now);
        });
        return taskMapper.batchInsert(tasks);
    }
}
