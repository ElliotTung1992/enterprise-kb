package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.constants.ReviewStatus;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.dto.CustomerAssistantResponse;
import com.enterprise.kb.search.model.ReviewRequest;
import com.enterprise.kb.search.service.CustomerAssistantService;
import com.enterprise.kb.search.service.ReviewRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerAssistantControllerTest {

    @Mock CustomerAssistantService customerAssistantService;
    @Mock ReviewRequestService reviewRequestService;
    @InjectMocks CustomerAssistantController controller;

    // -- behavior 1: approveReview 成功时 findById → resumeWithFeedback → approve 顺序执行 --

    @Test
    void approveReview_callsResumeBeforeApprove_onSuccess() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewRequest reviewReq = reviewRequest(id);
        CustomerAssistantResponse agentReply = new CustomerAssistantResponse("已批准", reviewReq.getSessionId());

        when(reviewRequestService.findById(id)).thenReturn(reviewReq);
        when(customerAssistantService.resumeWithFeedback(any(), any())).thenReturn(agentReply);

        try (MockedStatic<SecurityUtils> su = mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(reviewerId);

            var result = controller.approveReview(id, null);

            assertThat(result.getBody().getData().answer()).isEqualTo("已批准");
        }

        InOrder order = inOrder(reviewRequestService, customerAssistantService);
        order.verify(reviewRequestService).findById(id);
        order.verify(customerAssistantService).resumeWithFeedback(eq(reviewReq.getSessionId()), any());
        order.verify(reviewRequestService).approve(eq(id), eq(reviewerId), isNull());
    }

    // -- behavior 2: approveReview 失败时 approve 不被调用 --

    @Test
    void approveReview_doesNotCallApprove_whenResumeThrows() {
        UUID id = UUID.randomUUID();
        ReviewRequest reviewReq = reviewRequest(id);

        when(reviewRequestService.findById(id)).thenReturn(reviewReq);
        when(customerAssistantService.resumeWithFeedback(any(), any()))
                .thenThrow(new KbException("LLM 不可用", HttpStatus.INTERNAL_SERVER_ERROR));

        try (MockedStatic<SecurityUtils> su = mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> controller.approveReview(id, null))
                    .isInstanceOf(KbException.class)
                    .hasMessageContaining("LLM 不可用");
        }

        verify(reviewRequestService, never()).approve(any(), any(), any());
    }

    // -- behavior 3: rejectReview 成功时 findById → resumeWithFeedback → reject 顺序执行 --

    @Test
    void rejectReview_callsResumeBeforeReject_onSuccess() {
        UUID id = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        ReviewRequest reviewReq = reviewRequest(id);
        CustomerAssistantResponse agentReply = new CustomerAssistantResponse("已拒绝", reviewReq.getSessionId());

        when(reviewRequestService.findById(id)).thenReturn(reviewReq);
        when(customerAssistantService.resumeWithFeedback(any(), any())).thenReturn(agentReply);

        try (MockedStatic<SecurityUtils> su = mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(reviewerId);

            var result = controller.rejectReview(id, null);

            assertThat(result.getBody().getData().answer()).isEqualTo("已拒绝");
        }

        InOrder order = inOrder(reviewRequestService, customerAssistantService);
        order.verify(reviewRequestService).findById(id);
        order.verify(customerAssistantService).resumeWithFeedback(eq(reviewReq.getSessionId()), any());
        order.verify(reviewRequestService).reject(eq(id), eq(reviewerId), isNull());
    }

    // -- behavior 4: rejectReview 失败时 reject 不被调用 --

    @Test
    void rejectReview_doesNotCallReject_whenResumeThrows() {
        UUID id = UUID.randomUUID();
        ReviewRequest reviewReq = reviewRequest(id);

        when(reviewRequestService.findById(id)).thenReturn(reviewReq);
        when(customerAssistantService.resumeWithFeedback(any(), any()))
                .thenThrow(new KbException("LLM 超时", HttpStatus.GATEWAY_TIMEOUT));

        try (MockedStatic<SecurityUtils> su = mockStatic(SecurityUtils.class)) {
            su.when(SecurityUtils::getCurrentUserId).thenReturn(UUID.randomUUID());

            assertThatThrownBy(() -> controller.rejectReview(id, null))
                    .isInstanceOf(KbException.class)
                    .hasMessageContaining("LLM 超时");
        }

        verify(reviewRequestService, never()).reject(any(), any(), any());
    }

    // -- helpers --

    private ReviewRequest reviewRequest(UUID id) {
        ReviewRequest r = new ReviewRequest();
        r.setId(id);
        r.setSessionId(UUID.randomUUID());
        r.setStatus(ReviewStatus.PENDING);
        r.setToolCallId("call_xyz");
        r.setToolCallName("submitAfterSalesReview");
        return r;
    }
}
