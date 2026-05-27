package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.document.service.MdImageInput;
import com.enterprise.kb.document.service.MdImageUnderstandingResult;
import com.enterprise.kb.document.service.MdImageUnderstandingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 基于 DashScope OpenAI 兼容接口的 Markdown 图片理解实现。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enterprise.kb.md.image.vision-provider", havingValue = "DASHSCOPE", matchIfMissing = true)
public class DashScopeMdImageUnderstandingService implements MdImageUnderstandingService {

    private final ObjectMapper objectMapper;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${enterprise.kb.md.image.vision-model:qwen-vl-max}")
    private String model;

    @Value("${enterprise.kb.md.image.dashscope-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Override
    public MdImageUnderstandingResult understand(MdImageInput image) {
        if (!StringUtils.hasText(apiKey)) {
            throw new InvalidRequestException("未配置 DASHSCOPE_API_KEY，无法执行 Markdown 图片理解");
        }
        String content = callDashScope(image);
        return parseResult(content);
    }

    private String callDashScope(MdImageInput image) {
        String dataUrl = "data:%s;base64,%s".formatted(
                image.mimeType(), Base64.getEncoder().encodeToString(image.bytes()));
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", prompt(image)),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
                        )
                )),
                "temperature", 0.1
        );
        try {
            String response = RestClient.builder()
                    .baseUrl(stripTrailingSlash(baseUrl))
                    .build()
                    .post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new InvalidRequestException("调用 DashScope 图片理解失败: " + e.getMessage());
        }
    }

    private String prompt(MdImageInput image) {
        return """
                请理解这张 Markdown 文档中的图片，并只返回 JSON，不要返回 Markdown 代码块。
                JSON 字段固定为：
                {
                  "ocrText": "图中可读文字，没有则为空字符串",
                  "caption": "对图片内容的客观描述",
                  "summary": "适合知识库检索的简洁摘要",
                  "entities": "关键实体，逗号分隔，没有则为空字符串"
                }

                图片上下文：
                所在章节：%s
                图片标题：%s
                替代文本：%s
                对象路径：%s
                """.formatted(
                firstText(image.section(), "未命名章节"),
                firstText(image.title(), image.altText(), image.objectKey()),
                firstText(image.altText(), ""),
                image.objectKey());
    }

    private MdImageUnderstandingResult parseResult(String content) {
        try {
            String json = stripCodeFence(content);
            JsonNode root = objectMapper.readTree(json);
            String caption = root.path("caption").asText("");
            String summary = root.path("summary").asText("");
            if (!StringUtils.hasText(caption) || !StringUtils.hasText(summary)) {
                throw new InvalidRequestException("图片理解结果缺少 caption 或 summary");
            }
            return new MdImageUnderstandingResult(
                    root.path("ocrText").asText(""),
                    caption,
                    summary,
                    root.path("entities").asText(""));
        } catch (InvalidRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidRequestException("解析图片理解结果失败: " + e.getMessage());
        }
    }

    private String stripCodeFence(String content) {
        String value = content == null ? "" : content.strip();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "");
            value = value.replaceFirst("\\s*```$", "");
        }
        return value.strip();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

