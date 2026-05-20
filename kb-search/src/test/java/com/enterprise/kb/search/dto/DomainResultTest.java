package com.enterprise.kb.search.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DomainResult} 单测：awaitingSlot 索要订单号的启发式判定。
 */
class DomainResultTest {

    @Test
    void detectsOrderNumberRequestAsAwaitingSlot() {
        assertThat(DomainResult.of("好的，请提供您的订单号").awaitingSlot()).isTrue();
        assertThat(DomainResult.of("麻烦发我一下订单编号").awaitingSlot()).isTrue();
    }

    @Test
    void openEndedQuestionDoesNotLockRouting() {
        // 开放反问不算等待槽位——否则用户中途改投诉会被吞掉
        assertThat(DomainResult.of("您的订单符合退款条件，是否提交申请？").awaitingSlot()).isFalse();
        assertThat(DomainResult.of("请问您遇到了什么问题呢？").awaitingSlot()).isFalse();
    }

    @Test
    void terminalResultNeverAwaitsSlot() {
        assertThat(DomainResult.terminal("您的售后申请已提交，请等待审核。").awaitingSlot()).isFalse();
    }

    /**
     * 回归：LLM 用 markdown 列表跨行索要订单号是真实生产场景（投诉域典型回复）。
     * 早期 regex 把 \n 当作句末边界，多行格式直接断匹配，导致 awaitingSlot 漏报。
     */
    @Test
    void detectsMultiLineMarkdownSlotRequest() {
        String complaintAsk = """
                完全理解您的心情，处理缓慢确实会让人感到不满。

                为了帮您升级为正式投诉案件，请您提供以下信息：

                1. **订单号**：用于关联您的购物记录
                2. **投诉详情**：比如您之前反馈问题的渠道、反馈了多久、得到了什么回复等

                您方便提供这些信息吗？""";
        assertThat(DomainResult.of(complaintAsk).awaitingSlot()).isTrue();
    }

    /**
     * 回归：售后域 Agent 用 markdown 粗体包裹"订单号"，文本含多段换行。
     * 此前用户报 awaiting=false，需确认 regex 在该文本上正确命中。
     */
    @Test
    void detectsInlineAfterSalesSlotRequestWithMarkdown() {
        String afterSalesAsk = """
                您好！非常抱歉了解到您购买的杯子出现了裂痕，这确实会影响使用体验。

                为了帮您处理退货申请，请您提供一下**订单号**，我来为您查询售后资格。

                您可以在订单详情页面查看订单号，或者告诉我您大概是什么时候下单的，我可以帮您进一步查找。""";
        assertThat(DomainResult.of(afterSalesAsk).awaitingSlot()).isTrue();
    }

    @Test
    void doesNotMatchAcrossSentenceBoundary() {
        // 。 之后 regex 不应继续穿过——否则会误锁开放反问
        assertThat(DomainResult.of("您的订单已发货。请稍后告诉我们使用情况").awaitingSlot()).isFalse();
        assertThat(DomainResult.of("订单号已记录！请问还需要其他帮助吗？").awaitingSlot()).isFalse();
    }
}
