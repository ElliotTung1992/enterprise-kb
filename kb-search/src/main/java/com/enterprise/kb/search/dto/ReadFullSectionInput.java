package com.enterprise.kb.search.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Markdown Agentic RAG 读取完整 section 工具输入。
 *
 * @param parentId parent chunk ID
 */
public record ReadFullSectionInput(
        @JsonProperty(value = "parentId", required = true) String parentId
) {}
