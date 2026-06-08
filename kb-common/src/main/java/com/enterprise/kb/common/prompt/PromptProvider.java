package com.enterprise.kb.common.prompt;

import java.util.Map;

/**
 * Prompt 渲染入口。
 */
public interface PromptProvider {

    /**
     * 读取并渲染 prompt。
     *
     * @param name prompt 名称
     * @param vars 渲染变量
     * @return 渲染后的文本
     */
    String render(String name, Map<String, Object> vars);

    /**
     * 读取并渲染 prompt，同时返回版本信息。
     *
     * @param name prompt 名称
     * @param vars 渲染变量
     * @return 渲染结果
     */
    RenderedPrompt get(String name, Map<String, Object> vars);

    /**
     * 读取并渲染 prompt，同时允许实现把 prompt 版本写入当前 trace 上下文。
     *
     * <p>只给需要 prompt↔trace 关联的调用点使用。普通多 prompt 链路应继续调用
     * {@link #render(String, Map)}，避免同一 trace 内多个 prompt 互相覆盖版本信息。</p>
     *
     * @param name prompt 名称
     * @param vars 渲染变量
     * @return 渲染后的文本
     */
    default String renderForTrace(String name, Map<String, Object> vars) {
        return getForTrace(name, vars).text();
    }

    /**
     * 读取并渲染 prompt，同时允许实现把 prompt 版本写入当前 trace 上下文。
     *
     * @param name prompt 名称
     * @param vars 渲染变量
     * @return 渲染结果
     */
    default RenderedPrompt getForTrace(String name, Map<String, Object> vars) {
        return get(name, vars);
    }
}
