package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agentic 流式问答的「过程事件」协议（JSON-in-data）。
 *
 * <p>设计见 {@code docs/design-agentic-stream-process-events.md}。业务层 {@code Flux<String>} 的每个元素是
 * 一个 JSON payload 字符串，由 {@code type} 区分：{@code thinking} / {@code tool_call} / {@code tool_result}
 * / {@code answer} / {@code citations} / {@code done} / {@code error}。SSE 的 {@code data:} framing 由 Spring 自动完成，
 * 本类只负责把事件序列化为 JSON 字符串，以及从 JSON 事件中抽取「干净答案」供持久化 / tracing 复用。</p>
 */
public final class AgentStreamEvent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentStreamEvent() {
    }

    /**
     * 构造模型推理增量事件。
     *
     * @param delta 推理文本增量
     * @return JSON 事件字符串
     */
    public static String thinking(String delta) {
        return json(event("thinking", "delta", delta));
    }

    /**
     * 构造答案正文增量事件。
     *
     * @param delta 答案文本增量
     * @return JSON 事件字符串
     */
    public static String answer(String delta) {
        return json(event("answer", "delta", delta));
    }

    /**
     * 构造引用来源事件。
     *
     * @param citations 引用来源列表
     * @return JSON 事件字符串
     */
    public static String citations(List<Citation> citations) {
        return json(event("citations", "citations", citations));
    }

    /**
     * 构造流结束事件。
     *
     * @return JSON 事件字符串
     */
    public static String done() {
        return json(event("done"));
    }

    /**
     * 构造（脱敏后的）错误事件。
     *
     * @param message 错误说明
     * @return JSON 事件字符串
     */
    public static String error(String message) {
        return json(event("error", "message", message));
    }

    /**
     * 把一个事件 Map 序列化为 JSON 字符串。
     *
     * @param event 事件键值对（含 {@code type}）
     * @return JSON 字符串；序列化失败时返回一个 error 事件
     */
    public static String json(Map<String, Object> event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            return "{\"type\":\"error\",\"message\":\"event_serialize_failed\"}";
        }
    }

    /**
     * 新建仅含 {@code type} 的事件 Map，调用方继续 put 其余字段。
     *
     * @param type 事件类型
     * @return 含 type 的有序 Map
     */
    public static Map<String, Object> event(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        return m;
    }

    private static Map<String, Object> event(String type, String key, Object value) {
        Map<String, Object> m = event(type);
        m.put(key, value);
        return m;
    }

    /**
     * 持久化用：仅当事件为 {@code answer} 时返回其 delta，其余事件返回空串。
     * 入参约定为本协议的 JSON 事件字符串。
     *
     * @param element 流元素（JSON 事件）
     * @return answer 的 delta，或空串
     */
    public static String answerDelta(String element) {
        JsonNode node = tryRead(element);
        if (node == null) {
            return "";
        }
        JsonNode type = node.get("type");
        if (type != null && "answer".equals(type.asText())) {
            JsonNode delta = node.get("delta");
            return delta != null ? delta.asText("") : "";
        }
        return "";
    }

    private static JsonNode tryRead(String element) {
        if (element == null || element.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(element);
        } catch (Exception e) {
            return null;
        }
    }
}
