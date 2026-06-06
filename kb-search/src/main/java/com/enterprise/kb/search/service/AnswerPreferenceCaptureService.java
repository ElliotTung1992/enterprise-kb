package com.enterprise.kb.search.service;

import java.util.Optional;
import java.util.UUID;

/**
 * 对话内答案偏好捕获服务（ADR-016 决策 15，第二显式入口）。
 *
 * <p>从用户消息中识别"持久"的答案偏好声明（如"以后用中文回复我"），命中则写入用户画像的显式层（declared）
 * 并返回一句回执文案。一次性诉求（"这次详细点"）不写入。仅覆盖答案偏好（语言/长度/风格），不含资历。</p>
 */
public interface AnswerPreferenceCaptureService {

    /**
     * 捕获本轮消息中的持久答案偏好。
     *
     * @param userId  用户 ID
     * @param message 用户本轮消息
     * @return 回执文案（"已记住：…"）；未命中 / 关闭 / 失败时返回空
     */
    Optional<String> capture(UUID userId, String message);
}
