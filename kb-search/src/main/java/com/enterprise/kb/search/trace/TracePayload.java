package com.enterprise.kb.search.trace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace payload 构造工具。
 */
public final class TracePayload {

    private TracePayload() {
    }

    /**
     * 按传入顺序构造 payload map。
     *
     * @param keyValues key/value 交替参数
     * @return payload map
     */
    public static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }
}
