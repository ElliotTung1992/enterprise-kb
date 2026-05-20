package com.enterprise.kb.search.service;

/**
 * Trace 原始载荷脱敏服务。
 */
public interface TraceRedactionService {

    /**
     * 对 JSON 字符串中的敏感字段做遮挡。
     *
     * @param json JSON 字符串，可为空或非 JSON
     * @return 遮挡后的字符串；非 JSON 输入原样返回
     */
    String redactJson(String json);
}
