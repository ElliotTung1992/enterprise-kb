package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.ComplaintPlanningContext;
import com.enterprise.kb.search.dto.ComplaintResponsibilityDecision;
import com.enterprise.kb.search.model.Complaint;

/**
 * 投诉责任 LLM 兜底推理服务接口
 */
public interface ComplaintResponsibilityInferenceService {

    /**
     * 规则无法覆盖时，由 LLM 根据投诉和上下文推理责任方。
     *
     * @param complaint 投诉案件
     * @param context Planner 已收集的数据上下文
     * @return 责任认定结果
     */
    ComplaintResponsibilityDecision infer(Complaint complaint, ComplaintPlanningContext context);
}
