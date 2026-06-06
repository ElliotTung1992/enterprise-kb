package com.enterprise.kb.search.service;

import com.enterprise.kb.user.dto.InferredSignals;

import java.util.List;

/**
 * 用户画像统一推断服务（ADR-016，Phase 2）。
 *
 * <p>一次轻量 LLM 调用，从近期用户消息推断 资历/长度/语言/风格 四维信号（各带置信度）。
 * 用户在消息里**明说**的偏好（如"以后用中文"）应被识别为高置信；其余维从提问内容谨慎推断、证据不足给低置信或 NONE。
 * 写入时由 {@code ProfileService.recordInference} 逐字段过置信闸。</p>
 */
public interface ProfilePreferenceInferenceService {

    /**
     * 从近期用户消息推断 4 维画像信号。
     *
     * @param recentMessages 近期用户消息（最新在前）
     * @return 4 维 value+confidence；输入为空或调用失败时返回 {@link InferredSignals#empty()}
     */
    InferredSignals infer(List<String> recentMessages);
}
