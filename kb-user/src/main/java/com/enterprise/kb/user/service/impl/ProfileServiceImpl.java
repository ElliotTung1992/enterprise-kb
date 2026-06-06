package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.user.dto.ProfileInferenceState;
import com.enterprise.kb.user.dto.UpdateProfileRequest;
import com.enterprise.kb.user.dto.UserProfileData;
import com.enterprise.kb.user.dto.UserProfileView;
import com.enterprise.kb.user.mapper.UserProfileMapper;
import com.enterprise.kb.user.model.UserProfile;
import com.enterprise.kb.user.service.ProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户画像服务实现。
 *
 * <p>画像以 JSONB 原始字符串落库，本类用 Jackson 在 {@link UserProfileData} 与字符串间转换。
 * 服务态合并规则：同一字段「显式 &gt; 推断（达置信阈值）&gt; 空」。设计见 ADR-016。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    /** JSONB 序列化器：启用 JavaTime 以 ISO 串写入 Instant，反序列化忽略未知字段（向前兼容新维度）。 */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** 在线注入总开关（Phase 1）：关闭则 renderProfileBlock 一律返回空串。 */
    @Value("${enterprise.kb.profile.injection-enabled:true}")
    private boolean injectionEnabled;

    /** 置信阈值：推断资历低于此值不计入服务态（与离线 worker 共用同一配置）。 */
    @Value("${enterprise.kb.profile.inference.confidence-threshold:0.7}")
    private double confidenceThreshold;

    private final UserProfileMapper userProfileMapper;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public UserProfileView getProfile(UUID userId) {
        return toView(load(userId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateDeclared(UUID userId, UpdateProfileRequest req) {
        UserProfileData data = load(userId);
        // PUT 语义：declared 整体替换为请求值（null 即清空对应字段）。
        UserProfileData.Declared declared = new UserProfileData.Declared(
                req.seniority(), req.answerLength(), req.answerLanguage(), req.answerStyle());
        UserProfileData.Meta oldMeta = data.metaOrDefault();
        // 个性化开关仅在请求显式提供时更新，否则保留原值；推断调度元数据原样保留。
        boolean personalization = req.personalizationEnabled() != null
                ? req.personalizationEnabled() : oldMeta.personalizationEnabled();
        UserProfileData.Meta meta = new UserProfileData.Meta(
                personalization, oldMeta.lastInferenceAt(), oldMeta.processedMsgCount());
        persist(userId, new UserProfileData(declared, data.inferred(), meta));
        log.debug("更新用户显式画像：userId={}", userId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public String renderProfileBlock(UUID userId) {
        if (!injectionEnabled) {
            return "";
        }
        UserProfileData data = load(userId);
        if (!data.metaOrDefault().personalizationEnabled()) {
            return "";
        }
        UserProfileData.Declared d = data.declaredOrEmpty();
        Seniority seniority = servedSeniority(data);
        List<String> lines = new ArrayList<>();
        if (seniority != null) {
            lines.add("- 资历：" + seniorityLabel(seniority));
        }
        if (d.answerLength() != null) {
            lines.add("- 答案长度：" + lengthLabel(d.answerLength()));
        }
        if (d.answerLanguage() != null) {
            lines.add("- 回答语言：" + languageLabel(d.answerLanguage()));
        }
        if (d.answerStyle() != null) {
            lines.add("- 答案风格：" + styleLabel(d.answerStyle()));
        }
        if (lines.isEmpty()) {
            return "";
        }
        return """
                <user_profile>
                以下是当前用户的长期偏好，作为默认参考。若用户在本轮提问中另有明确要求，以本轮要求为准。
                %s
                </user_profile>
                """.formatted(String.join("\n", lines));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public ProfileInferenceState getInferenceState(UUID userId) {
        UserProfileData data = load(userId);
        UserProfileData.Meta meta = data.metaOrDefault();
        return new ProfileInferenceState(
                data.declaredOrEmpty().seniority() != null,
                meta.personalizationEnabled(),
                meta.lastInferenceAt(),
                meta.processedMsgCount());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void recordInference(UUID userId, Seniority seniority, Double confidence, int processedMsgCount) {
        UserProfileData data = load(userId);
        // seniority 非空才更新推断层；为 null 时保留旧推断，仅刷新调度元数据用于去抖。
        UserProfileData.Inferred inferred = seniority != null
                ? new UserProfileData.Inferred(seniority, confidence, Instant.now())
                : data.inferred();
        UserProfileData.Meta old = data.metaOrDefault();
        UserProfileData.Meta meta = new UserProfileData.Meta(
                old.personalizationEnabled(), Instant.now(), processedMsgCount);
        // 绝不触碰 declared。
        persist(userId, new UserProfileData(data.declared(), inferred, meta));
        log.debug("记录离线推断：userId={}，applied={}，processedMsgCount={}", userId, seniority != null, processedMsgCount);
    }

    // ---- private helpers ----

    private UserProfileData load(UUID userId) {
        return userProfileMapper.findByUserId(userId)
                .map(UserProfile::getProfile)
                .map(this::parse)
                .orElseGet(UserProfileData::empty);
    }

    private UserProfileData parse(String json) {
        if (json == null || json.isBlank()) {
            return UserProfileData.empty();
        }
        try {
            return MAPPER.readValue(json, UserProfileData.class);
        } catch (JsonProcessingException e) {
            log.warn("解析用户画像 JSON 失败，按空画像处理：{}", e.getMessage());
            return UserProfileData.empty();
        }
    }

    private void persist(UUID userId, UserProfileData data) {
        UserProfile entity = new UserProfile();
        entity.setUserId(userId);
        try {
            entity.setProfile(MAPPER.writeValueAsString(data));
        } catch (JsonProcessingException e) {
            throw new KbException("序列化用户画像失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        entity.setUpdatedAt(Instant.now());
        userProfileMapper.upsert(entity);
    }

    /** 服务态资历：显式优先；显式为空时取置信达标的推断值，否则 null。 */
    private Seniority servedSeniority(UserProfileData data) {
        Seniority declared = data.declaredOrEmpty().seniority();
        if (declared != null) {
            return declared;
        }
        UserProfileData.Inferred inf = data.inferredOrEmpty();
        if (inf.seniority() != null && inf.confidence() != null && inf.confidence() >= confidenceThreshold) {
            return inf.seniority();
        }
        return null;
    }

    private UserProfileView toView(UserProfileData data) {
        UserProfileData.Declared d = data.declaredOrEmpty();
        Seniority served = servedSeniority(data);
        boolean fromExplicit = d.seniority() != null;
        String source = served == null ? null : (fromExplicit ? "EXPLICIT" : "INFERRED");
        Double confidence = (served != null && !fromExplicit) ? data.inferredOrEmpty().confidence() : null;
        return new UserProfileView(served, source, confidence,
                d.answerLength(), d.answerLanguage(), d.answerStyle(),
                data.metaOrDefault().personalizationEnabled());
    }

    private String seniorityLabel(Seniority s) {
        return switch (s) {
            case JUNIOR -> "初级（需从基础概念讲起）";
            case INTERMEDIATE -> "中级（可假定具备基础概念，无需从零解释）";
            case SENIOR -> "高级（可直接深入细节与权衡）";
        };
    }

    private String lengthLabel(AnswerLength l) {
        return switch (l) {
            case CONCISE -> "简洁";
            case MEDIUM -> "适中";
            case DETAILED -> "详细";
        };
    }

    private String languageLabel(AnswerLanguage l) {
        return switch (l) {
            case ZH -> "中文";
            case EN -> "英文";
            case FOLLOW -> "跟随提问语言";
        };
    }

    private String styleLabel(AnswerStyle s) {
        return switch (s) {
            case DIRECT -> "直接给结论";
            case EXPLAINED -> "带解释";
            case WITH_EXAMPLES -> "带示例";
        };
    }
}
