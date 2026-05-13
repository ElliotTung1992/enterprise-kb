package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.ReviewStatus;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.mapper.ReviewRequestMapper;
import com.enterprise.kb.search.model.ReviewRequest;
import com.enterprise.kb.search.service.ReviewRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 审核申请服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewRequestServiceImpl implements ReviewRequestService {

    private final ReviewRequestMapper reviewRequestMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ReviewRequest createPending(UUID sessionId, UUID spaceId, UUID userId,
                                       String orderId, String reason,
                                       String conversationSnapshot, String orderDetails,
                                       String toolCallId, String toolCallName) {
        Instant now = Instant.now();
        ReviewRequest req = new ReviewRequest();
        req.setId(UUID.randomUUID());
        req.setSessionId(sessionId);
        req.setSpaceId(spaceId);
        req.setUserId(userId);
        req.setOrderId(orderId);
        req.setReason(reason);
        req.setConversationSnapshot(conversationSnapshot);
        req.setOrderDetails(orderDetails);
        req.setToolCallId(toolCallId);
        req.setToolCallName(toolCallName);
        req.setStatus(ReviewStatus.PENDING);
        req.setCreatedAt(now);
        req.setUpdatedAt(now);
        reviewRequestMapper.insert(req);
        log.info("创建审核申请：id={}，sessionId={}，orderId={}", req.getId(), sessionId, orderId);
        return req;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ReviewRequest findById(UUID id) {
        return reviewRequestMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewRequest", id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReviewRequest> listPending(UUID spaceId) {
        return reviewRequestMapper.findPendingBySpaceId(spaceId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReviewRequest> listAllPending() {
        return reviewRequestMapper.findByStatus(ReviewStatus.PENDING);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public List<ReviewRequest> listByStatus(@Nullable ReviewStatus status) {
        return reviewRequestMapper.findByStatus(status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ReviewRequest approve(UUID id, UUID reviewerId, String comment) {
        ReviewRequest req = reviewRequestMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewRequest", id));
        Instant now = Instant.now();
        reviewRequestMapper.updateDecision(id, ReviewStatus.APPROVED, reviewerId, comment, now, now);
        req.setStatus(ReviewStatus.APPROVED);
        log.info("审核通过：id={}，reviewerId={}", id, reviewerId);
        return req;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ReviewRequest reject(UUID id, UUID reviewerId, String comment) {
        ReviewRequest req = reviewRequestMapper.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ReviewRequest", id));
        Instant now = Instant.now();
        reviewRequestMapper.updateDecision(id, ReviewStatus.REJECTED, reviewerId, comment, now, now);
        req.setStatus(ReviewStatus.REJECTED);
        log.info("审核拒绝：id={}，reviewerId={}", id, reviewerId);
        return req;
    }
}
