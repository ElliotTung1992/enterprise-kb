package com.enterprise.kb.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重命名会话请求。
 *
 * @param title 新会话标题
 */
public record RenameSessionRequest(
        @NotBlank @Size(max = 200) String title
) {}
