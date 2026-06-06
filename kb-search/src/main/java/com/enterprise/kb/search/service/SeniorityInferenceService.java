package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SeniorityInference;

import java.util.List;
import java.util.Optional;

/**
 * 资历推断服务：从用户历史提问推断其资历水平（ADR-016，Phase 2 离线链路）。
 *
 * <p>用一次轻量 LLM 调用完成分类，输出资历枚举 + 置信度 + 理由；调用方按置信阈值决定是否应用。</p>
 */
public interface SeniorityInferenceService {

    /**
     * 基于近期提问推断用户资历。
     *
     * @param recentQuestions 近期提问正文（最新在前）
     * @return 推断结果；输入为空、无法解析或调用失败时返回空
     */
    Optional<SeniorityInference> infer(List<String> recentQuestions);
}
