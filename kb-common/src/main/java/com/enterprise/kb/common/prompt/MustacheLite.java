package com.enterprise.kb.common.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 极简 mustache 变量渲染器，仅支持 {{var}}。
 */
public class MustacheLite {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

    /**
     * 渲染模板。
     *
     * @param template 模板
     * @param vars     变量
     * @return 渲染结果
     */
    public String render(String template, Map<String, Object> vars) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Map<String, Object> safeVars = vars == null ? Map.of() : vars;
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!safeVars.containsKey(key)) {
                throw new PromptRenderException("Prompt 缺少变量: " + key);
            }
            Object value = safeVars.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
