package com.enterprise.kb.user.service.impl;

import com.enterprise.kb.common.constants.AnswerLanguage;
import com.enterprise.kb.common.constants.AnswerLength;
import com.enterprise.kb.common.constants.AnswerStyle;
import com.enterprise.kb.common.constants.Seniority;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.user.dto.InferredSignals;
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
 * 服务态合并规则：每维「显式 &gt; 推断（写入时已过置信闸）&gt; 空」。设计见 ADR-016。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    /** JSONB 序列化器：启用 JavaTime 以 ISO 串写 Instant，反序列化忽略未知字段（向前兼容）。 */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    /** 在线注入总开关（Phase 1）：关闭则 renderProfileBlock 一律返回空串。 */
    @Value("${enterprise.kb.profile.injection-enabled:true}")
    private boolean injectionEnabled;

    /** 置信阈值：推断值低于此值不写入（写门），与离线 worker 共用同一配置。 */
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
    @Transactional
    public void recordInference(UUID userId, InferredSignals s, int processedMsgCount) {
        UserProfileData data = load(userId);
        UserProfileData.Inferred old = data.inferredOrEmpty();
        // 逐字段写门：达置信才更新该维，否则保留旧值（避免低置信覆盖、抖动）。
        boolean[] changed = {false};
        Seniority sen = gate(s.seniority(), s.seniorityConfidence(), old.seniority(), changed);
        AnswerLength len = gate(s.answerLength(), s.answerLengthConfidence(), old.answerLength(), changed);
        AnswerLanguage lang = gate(s.answerLanguage(), s.answerLanguageConfidence(), old.answerLanguage(), changed);
        AnswerStyle sty = gate(s.answerStyle(), s.answerStyleConfidence(), old.answerStyle(), changed);
        Instant inferredAt = changed[0] ? Instant.now() : old.inferredAt();
        UserProfileData.Inferred inferred = new UserProfileData.Inferred(sen, len, lang, sty, inferredAt);
        UserProfileData.Meta m = data.metaOrDefault();
        UserProfileData.Meta meta = new UserProfileData.Meta(
                m.personalizationEnabled(), Instant.now(), processedMsgCount);
        // 绝不触碰 declared。
        persist(userId, new UserProfileData(data.declared(), inferred, meta));
        log.debug("记录统一推断：userId={}，changed={}，processedMsgCount={}", userId, changed[0], processedMsgCount);
    }

    /** 写门：新值非空且置信达标则采用（标记 changed），否则保留旧值。 */
    private <T> T gate(T newValue, Double confidence, T oldValue, boolean[] changed) {
        if (newValue != null && confidence != null && confidence >= confidenceThreshold) {
            changed[0] = true;
            return newValue;
        }
        return oldValue;
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
        List<String> lines = new ArrayList<>();
        Seniority sen = servedSeniority(data);
        if (sen != null) {
            lines.add("- 资历：" + seniorityLabel(sen));
        }
        AnswerLength len = servedLength(data);
        if (len != null) {
            lines.add("- 答案长度：" + lengthLabel(len));
        }
        AnswerLanguage lang = servedLanguage(data);
        if (lang != null) {
            lines.add("- 回答语言：" + languageLabel(lang));
        }
        AnswerStyle sty = servedStyle(data);
        if (sty != null) {
            lines.add("- 答案风格：" + styleLabel(sty));
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

    // ---- 服务态合并：显式优先，否则取推断（推断已过写门） ----

    private Seniority servedSeniority(UserProfileData d) {
        Seniority declared = d.declaredOrEmpty().seniority();
        return declared != null ? declared : d.inferredOrEmpty().seniority();
    }

    private AnswerLength servedLength(UserProfileData d) {
        AnswerLength declared = d.declaredOrEmpty().answerLength();
        return declared != null ? declared : d.inferredOrEmpty().answerLength();
    }

    private AnswerLanguage servedLanguage(UserProfileData d) {
        AnswerLanguage declared = d.declaredOrEmpty().answerLanguage();
        return declared != null ? declared : d.inferredOrEmpty().answerLanguage();
    }

    private AnswerStyle servedStyle(UserProfileData d) {
        AnswerStyle declared = d.declaredOrEmpty().answerStyle();
        return declared != null ? declared : d.inferredOrEmpty().answerStyle();
    }

    private UserProfileView toView(UserProfileData data) {
        UserProfileData.Declared dd = data.declaredOrEmpty();
        UserProfileData.Inferred ii = data.inferredOrEmpty();
        return new UserProfileView(
                servedSeniority(data), source(dd.seniority(), ii.seniority()),
                servedLength(data), source(dd.answerLength(), ii.answerLength()),
                servedLanguage(data), source(dd.answerLanguage(), ii.answerLanguage()),
                servedStyle(data), source(dd.answerStyle(), ii.answerStyle()),
                data.metaOrDefault().personalizationEnabled());
    }

    /** 来源标注：显式优先。 */
    private String source(Object declared, Object inferred) {
        if (declared != null) {
            return "EXPLICIT";
        }
        if (inferred != null) {
            return "INFERRED";
        }
        return null;
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
