package com.enterprise.kb.search.service.impl;

import com.enterprise.kb.common.constants.ResponsibleParty;
import com.enterprise.kb.common.prompt.PromptProvider;
import com.enterprise.kb.search.ai.ModelProviderResolver;
import com.enterprise.kb.search.dto.ComplaintPlanningContext;
import com.enterprise.kb.search.dto.ComplaintResponsibilityDecision;
import com.enterprise.kb.search.model.Complaint;
import com.enterprise.kb.search.service.ComplaintResponsibilityInferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 投诉责任 LLM 兜底推理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintResponsibilityInferenceServiceImpl implements ComplaintResponsibilityInferenceService {

    private static final String RESPONSIBILITY_PROMPT = "kb/complaint/responsibility";

    private final ModelProviderResolver modelProviderResolver;
    private final PromptProvider promptProvider;

    /**
     * {@inheritDoc}
     */
    @Override
    public ComplaintResponsibilityDecision infer(Complaint complaint, ComplaintPlanningContext context) {
        try {
            ChatClient chatClient = modelProviderResolver.resolveChatClient(null);
            String content = chatClient.prompt()
                    .system(promptProvider.render(RESPONSIBILITY_PROMPT, java.util.Map.of()))
                    .user(buildUserPrompt(complaint, context))
                    .call()
                    .content();
            return parseDecision(content);
        } catch (Exception e) {
            log.warn("LLM 投诉责任推理失败，转为 DISPUTED：complaintId={}", complaint.getId(), e);
            return new ComplaintResponsibilityDecision(ResponsibleParty.DISPUTED,
                    "AI 责任推理失败或证据不足，需人工判定。");
        }
    }

    private String buildUserPrompt(Complaint complaint, ComplaintPlanningContext context) {
        return """
                投诉内容：%s
                订单：%s，金额：%s，状态：%s
                物流：已签收=%s，轨迹中断超48h=%s，签收7天内=%s
                历史投诉：%d 次，未解决：%d 次
                商家违规次数：%d
                """.formatted(
                complaint.getContent(),
                context.orderDetails().orderId(),
                context.orderDetails().amount(),
                context.orderDetails().status(),
                context.logisticsTrace().delivered(),
                context.logisticsTrace().interruptedOver48h(),
                context.logisticsTrace().deliveredWithin7Days(),
                context.complaintHistory().complaintCount(),
                context.complaintHistory().unresolvedCount(),
                context.merchantViolationRecord().violationCount()
        );
    }

    private ComplaintResponsibilityDecision parseDecision(String content) {
        if (content == null || content.isBlank()) {
            return new ComplaintResponsibilityDecision(ResponsibleParty.DISPUTED,
                    "AI 未给出明确责任认定，需人工判定。");
        }
        String[] parts = content.strip().split("\\|", 2);
        ResponsibleParty party;
        try {
            party = ResponsibleParty.valueOf(parts[0].strip());
        } catch (Exception e) {
            party = ResponsibleParty.DISPUTED;
        }
        String reason = parts.length > 1 && !parts[1].isBlank()
                ? parts[1].strip()
                : "AI 未给出充分理由，需人工复核。";
        return new ComplaintResponsibilityDecision(party, reason);
    }
}
