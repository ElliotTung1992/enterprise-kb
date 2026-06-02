package com.enterprise.kb.search.service.impl;

import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.enterprise.kb.search.dto.SearchHit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RerankServiceImplTest {

    @Test
    void rerankPreservesConfiguredModelWhenSettingPerCallTopN() {
        RerankModel model = mock(RerankModel.class);
        when(model.call(org.mockito.ArgumentMatchers.any(RerankRequest.class)))
                .thenReturn(new RerankResponse(List.of()));
        RerankServiceImpl service = new RerankServiceImpl(model);
        ReflectionTestUtils.setField(service, "rerankModelName", "gte-rerank-v2");

        service.rerank("退款流程", List.of(hit()), 1);

        ArgumentCaptor<RerankRequest> captor = ArgumentCaptor.forClass(RerankRequest.class);
        verify(model).call(captor.capture());
        DashScopeRerankOptions options = (DashScopeRerankOptions) captor.getValue().getOptions();
        assertThat(options.getModel()).isEqualTo("gte-rerank-v2");
        assertThat(options.getTopN()).isEqualTo(1);
    }

    private SearchHit hit() {
        return new SearchHit("chunk-1", UUID.randomUUID(), "文档.md", "退款流程说明",
                null, 1.0, "text/markdown", "TEXT", null, null, null,
                "退款", null);
    }
}
