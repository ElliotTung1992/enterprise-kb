package com.enterprise.kb.common.constants;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Domain} 单测：业务域与特殊路由出口的区分。
 */
class DomainTest {

    @Test
    void afterSalesAndComplaintAreBusinessDomains() {
        assertThat(Domain.AFTER_SALES.isBusinessDomain()).isTrue();
        assertThat(Domain.COMPLAINT.isBusinessDomain()).isTrue();
    }

    @Test
    void handoffChitchatUnclearAreNotBusinessDomains() {
        assertThat(Domain.HANDOFF.isBusinessDomain()).isFalse();
        assertThat(Domain.CHITCHAT.isBusinessDomain()).isFalse();
        assertThat(Domain.UNCLEAR.isBusinessDomain()).isFalse();
    }
}
