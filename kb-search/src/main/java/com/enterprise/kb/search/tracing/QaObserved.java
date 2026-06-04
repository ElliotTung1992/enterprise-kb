package com.enterprise.kb.search.tracing;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个问答 Controller 方法为「一次请求的根 span 边界」（ADR-015 根 span 边界化）。
 *
 * <p>被标注的方法由 {@link QaObservedAspect} 环绕：在方法 AOP 边界统一创建业务根 observation
 * （同步接口方法返回后关闭、流式接口交给 {@code Flux} 终止信号关闭），从方法参数抽取业务身份并
 * 写 trace input/output。Service 不再自建根 span，只负责业务。</p>
 *
 * <p>仅在 {@code enterprise.kb.tracing.enabled=true} 时 aspect 装配生效；关闭时本注解为空标记。</p>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QaObserved {

    /**
     * 根 span 名称，如 {@code kb.qa.ask} / {@code kb.qa.ask.agentic} /
     * {@code kb.qa.ask.stream} / {@code kb.qa.ask.agentic.stream}。
     *
     * @return span 名称
     */
    String name();
}
