package com.enterprise.kb.ielts.service.impl;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.ielts.mapper.IeltsPronunciationPointMapper;
import com.enterprise.kb.ielts.model.IeltsPronunciationPoint;
import com.enterprise.kb.ielts.service.IeltsPronunciationPointService;
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
public class IeltsPronunciationPointServiceImpl implements IeltsPronunciationPointService {

    private final IeltsPronunciationPointMapper pointMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IeltsPronunciationPoint> listPoints(Integer difficulty, String category, int page, int size) {
        PageHelper.startPage(page, size);
        List<IeltsPronunciationPoint> list = pointMapper.findAll(difficulty, category);
        return PageResponse.of(new PageInfo<>(list));
    }

    @Override
    @Transactional(readOnly = true)
    public IeltsPronunciationPoint getById(UUID id) {
        return pointMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IeltsPronunciationPoint", id));
    }

    @Override
    @Transactional
    public IeltsPronunciationPoint create(IeltsPronunciationPoint point) {
        point.setId(UUID.randomUUID());
        Instant now = Instant.now();
        point.setCreatedAt(now);
        point.setUpdatedAt(now);
        pointMapper.insert(point);
        return point;
    }

    @Override
    @Transactional
    public IeltsPronunciationPoint update(UUID id, IeltsPronunciationPoint point) {
        IeltsPronunciationPoint existing = getById(id);
        point.setId(existing.getId());
        point.setCreatedAt(existing.getCreatedAt());
        point.setUpdatedAt(Instant.now());
        pointMapper.update(point);
        return point;
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        getById(id);
        pointMapper.deleteById(id);
    }

    @Override
    @Transactional
    public int batchImport(List<IeltsPronunciationPoint> points) {
        if (points == null || points.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now();
        points.forEach(p -> {
            if (p.getId() == null) p.setId(UUID.randomUUID());
            if (p.getCreatedAt() == null) p.setCreatedAt(now);
            if (p.getUpdatedAt() == null) p.setUpdatedAt(now);
        });
        return pointMapper.batchInsert(points);
    }
}
