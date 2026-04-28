package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agentic RAG 工具调用的输入参数，LLM 自主决定检索关键词。
 */
public record KnowledgeSearchInput(
        @JsonProperty(value = "query", required = true) String query
) {}
