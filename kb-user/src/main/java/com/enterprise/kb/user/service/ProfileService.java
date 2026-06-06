package com.enterprise.kb.user.service;

import com.enterprise.kb.user.dto.InferredSignals;
import com.enterprise.kb.user.dto.ProfileInferenceState;
import com.enterprise.kb.user.dto.UpdateProfileRequest;
import com.enterprise.kb.user.dto.UserProfileView;

import java.util.UUID;

/**
 * 用户画像服务：管理持久画像的读写，并为问答提供 prompt 注入文本。
 *
 * <p>画像分显式（设置页声明，优先级最高）与推断（离线统一推断 4 维、过置信闸）两层，
 * 统一存于单一 user_profiles 表的 JSONB 列。设计见 ADR-016。</p>
 */
public interface ProfileService {

    /**
     * 查询用户画像服务态视图（每维合并显式与推断、标注来源）。
     *
     * @param userId 用户 ID
     * @return 画像视图；无画像时各字段为 null、个性化默认开启
     */
    UserProfileView getProfile(UUID userId);

    /**
     * 更新用户显式声明（同步直写、即时生效，不影响推断层）。
     *
     * @param userId 用户 ID
     * @param req    更新请求（PUT 语义：整体替换 declared，null 字段清空）
     */
    void updateDeclared(UUID userId, UpdateProfileRequest req);

    /**
     * 渲染注入问答系统提示的 {@code <user_profile>} 软默认块。
     *
     * @param userId 用户 ID
     * @return 软默认块文本；个性化关闭、无任何可用偏好、或注入开关关闭时返回空串
     */
    String renderProfileBlock(UUID userId);

    /**
     * 读取离线推断调度所需的画像状态快照（供 worker 去抖判断）。
     *
     * @param userId 用户 ID
     * @return 画像状态快照
     */
    ProfileInferenceState getInferenceState(UUID userId);

    /**
     * 记录一次离线统一推断结果：逐字段过置信闸（达标才更新对应 inferred 维、否则保留旧值），并刷新调度元数据。
     * 绝不触碰 declared。
     *
     * @param userId            用户 ID
     * @param signals           本次推断 4 维原始输出（value + confidence）
     * @param processedMsgCount 本次推断已处理的用户消息总数（写入元数据用于下次去抖）
     */
    void recordInference(UUID userId, InferredSignals signals, int processedMsgCount);
}
