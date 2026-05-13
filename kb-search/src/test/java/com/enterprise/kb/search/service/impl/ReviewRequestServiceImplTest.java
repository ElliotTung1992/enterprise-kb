package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.ReviewStatus;
import com.enterprise.kb.common.exception.ResourceNotFoundException;
import com.enterprise.kb.search.mapper.ReviewRequestMapper;
import com.enterprise.kb.search.model.ReviewRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewRequestServiceImplTest {

    @Mock ReviewRequestMapper reviewRequestMapper;
    @InjectMocks ReviewRequestServiceImpl service;

    // -- behavior 1: createPending assigns PENDING and copies all fields --

    @Test
    void createPendingAssignsPendingStatusAndCopiesAllFields() {
        UUID sessionId = UUID.randomUUID();
        UUID spaceId   = UUID.randomUUID();
        UUID userId    = UUID.randomUUID();

        ArgumentCaptor<ReviewRequest> captor = ArgumentCaptor.forClass(ReviewRequest.class);

        service.createPending(sessionId, spaceId, userId,
                "ORD-001", "退款申请", "{}", "{}", "call_abc", "submitAfterSalesReview");

        verify(reviewRequestMapper).insert(captor.capture());
        ReviewRequest saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(saved.getSessionId()).isEqualTo(sessionId);
        assertThat(saved.getSpaceId()).isEqualTo(spaceId);
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getOrderId()).isEqualTo("ORD-001");
        assertThat(saved.getReason()).isEqualTo("退款申请");
        assertThat(saved.getToolCallId()).isEqualTo("call_abc");
        assertThat(saved.getToolCallName()).isEqualTo("submitAfterSalesReview");
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // -- behavior 2: approve returns the ReviewRequest with sessionId and delegates status update to mapper --

    @Test
    void approveReturnsReviewRequestAndUpdatesStatusToApproved() {
        UUID id        = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        ReviewRequest existing = pendingRequest(id, sessionId);
        when(reviewRequestMapper.findById(id)).thenReturn(Optional.of(existing));

        ReviewRequest returned = service.approve(id, reviewerId, "符合条件");

        assertThat(returned.getSessionId()).isEqualTo(sessionId);
        assertThat(returned.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        verify(reviewRequestMapper).updateDecision(eq(id), eq(ReviewStatus.APPROVED), eq(reviewerId),
                eq("符合条件"), any(), any());
    }

    // -- behavior 3: reject returns the ReviewRequest with sessionId and delegates status update to mapper --

    @Test
    void rejectReturnsReviewRequestAndUpdatesStatusToRejected() {
        UUID id        = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        ReviewRequest existing = pendingRequest(id, sessionId);
        when(reviewRequestMapper.findById(id)).thenReturn(Optional.of(existing));

        ReviewRequest returned = service.reject(id, reviewerId, "不符合退款条件");

        assertThat(returned.getSessionId()).isEqualTo(sessionId);
        assertThat(returned.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        verify(reviewRequestMapper).updateDecision(eq(id), eq(ReviewStatus.REJECTED), eq(reviewerId),
                eq("不符合退款条件"), any(), any());
    }

    // -- behavior 4: findById throws ResourceNotFoundException for unknown id --

    @Test
    void findByIdThrowsResourceNotFoundExceptionWhenNotExists() {
        UUID unknownId = UUID.randomUUID();
        when(reviewRequestMapper.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -- behavior 5: approve throws ResourceNotFoundException for unknown id --

    @Test
    void approveThrowsResourceNotFoundExceptionWhenRequestNotExists() {
        UUID unknownId = UUID.randomUUID();
        when(reviewRequestMapper.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(unknownId, UUID.randomUUID(), null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -- behavior 6: listPending delegates to mapper --

    @Test
    void listPendingReturnsPendingRequestsForSpace() {
        UUID spaceId = UUID.randomUUID();
        ReviewRequest pending = pendingRequest(UUID.randomUUID(), UUID.randomUUID());
        when(reviewRequestMapper.findPendingBySpaceId(spaceId)).thenReturn(List.of(pending));

        List<ReviewRequest> result = service.listPending(spaceId);

        assertThat(result).containsExactly(pending);
    }

    // -- behavior 7: listByStatus delegates to mapper with given status --

    @Test
    void listByStatusDelegatesToMapperWithGivenStatus() {
        ReviewRequest r = pendingRequest(UUID.randomUUID(), UUID.randomUUID());
        when(reviewRequestMapper.findByStatus(ReviewStatus.PENDING)).thenReturn(List.of(r));

        List<ReviewRequest> result = service.listByStatus(ReviewStatus.PENDING);

        assertThat(result).containsExactly(r);
        verify(reviewRequestMapper).findByStatus(ReviewStatus.PENDING);
    }

    // -- behavior 8: listByStatus(null) returns all records --

    @Test
    void listByStatusWithNullReturnsAllRecords() {
        ReviewRequest r1 = pendingRequest(UUID.randomUUID(), UUID.randomUUID());
        ReviewRequest r2 = pendingRequest(UUID.randomUUID(), UUID.randomUUID());
        r2.setStatus(ReviewStatus.APPROVED);
        when(reviewRequestMapper.findByStatus(null)).thenReturn(List.of(r1, r2));

        List<ReviewRequest> result = service.listByStatus(null);

        assertThat(result).hasSize(2);
        verify(reviewRequestMapper).findByStatus(null);
    }

    // -- helpers --

    private ReviewRequest pendingRequest(UUID id, UUID sessionId) {
        ReviewRequest r = new ReviewRequest();
        r.setId(id);
        r.setSessionId(sessionId);
        r.setStatus(ReviewStatus.PENDING);
        r.setToolCallId("call_xyz");
        r.setToolCallName("submitAfterSalesReview");
        return r;
    }
}
