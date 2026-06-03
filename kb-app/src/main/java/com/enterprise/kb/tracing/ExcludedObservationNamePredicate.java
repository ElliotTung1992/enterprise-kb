package com.enterprise.kb.tracing;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;

import java.util.List;
import java.util.Locale;

/**
 * 基础设施 span 噪音过滤 predicate（design-langfuse-noise-filter）。
 *
 * <p>按 observation <b>name</b> 做<b>大小写不敏感前缀黑名单</b>匹配：命中任一前缀的 observation
 * 直接判 NOOP（根本不生成 span），其余照常放行。注册在 {@code ObservationRegistry} 上后，
 * 由 Spring Boot 的 {@code ObservationAutoConfiguration} 自动收集，无需手工 customizer。</p>
 *
 * <p>过滤在 Micrometer 源头一次拦掉，下游零 span 分配开销（D1）；采用黑名单策略，业务链路与
 * 未知 observation 默认放行，仅丢点名的基础设施噪音（D2）。匹配只看 name，不看 tag/属性（D4）。</p>
 *
 * <p><b>副作用（D5）</b>：predicate 决定 Observation 是否存在，被 metrics 与 tracing 共用，
 * 命中黑名单时该 observation 的 metrics 一并消失。命中项基本无监控价值（security/authorize/
 * scheduler/health），已接受此取舍。</p>
 */
public class ExcludedObservationNamePredicate implements ObservationPredicate {

    /** 归一化后的黑名单前缀（已 trim、去空、转小写）。 */
    private final List<String> normalizedPrefixes;

    /**
     * @param excludedPrefixes 黑名单前缀原始配置；构造时统一 trim、丢弃空白项、转小写。
     *                         传 {@code null} 或全为空白时，等价于放行全部 observation。
     */
    public ExcludedObservationNamePredicate(List<String> excludedPrefixes) {
        this.normalizedPrefixes = excludedPrefixes == null ? List.of()
                : excludedPrefixes.stream()
                        .filter(p -> p != null && !p.isBlank())
                        .map(p -> p.trim().toLowerCase(Locale.ROOT))
                        .toList();
    }

    /**
     * @return {@code true} 放行（生成 span），{@code false} 命中黑名单 → NOOP（不产 span）。
     */
    @Override
    public boolean test(String name, Observation.Context context) {
        System.out.println("com.enterprise.kb.tracing.ExcludedObservationNamePredicate.test:" + name);
        if (name == null || normalizedPrefixes.isEmpty()) {
            return true;
        }
        String lowerName = name.toLowerCase(Locale.ROOT);
        for (String prefix : normalizedPrefixes) {
            if (lowerName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }
}
