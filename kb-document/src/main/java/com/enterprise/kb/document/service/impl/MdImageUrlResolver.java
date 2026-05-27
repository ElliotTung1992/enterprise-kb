package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.common.exception.InvalidRequestException;
import com.enterprise.kb.document.service.MdImageReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * Markdown 图片 MinIO URL 解析器。
 */
@Component
public class MdImageUrlResolver {

    private final String urlPrefix;

    public MdImageUrlResolver(
            @Value("${enterprise.kb.md.image.minio-endpoint:${enterprise.kb.storage.minio.endpoint:http://localhost:9000}}")
            String minioEndpoint,
            @Value("${enterprise.kb.md.image.bucket:${enterprise.kb.storage.minio.bucket:kb-assets}}")
            String bucket) {
        String endpoint = stripTrailingSlash(minioEndpoint);
        this.urlPrefix = endpoint + "/" + bucket + "/";
    }

    /**
     * 校验完整 MinIO URL 并解析 objectKey。
     *
     * @param imageUrl Markdown 图片 URL
     * @return 图片引用
     */
    public MdImageReference resolve(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            throw new InvalidRequestException("Markdown 图片地址不能为空");
        }
        String normalizedUrl = imageUrl.strip();
        validateUri(normalizedUrl);
        if (!normalizedUrl.startsWith(urlPrefix)) {
            throw new InvalidRequestException("Markdown 图片地址必须以 " + urlPrefix + " 开头");
        }
        String objectKey = normalizedUrl.substring(urlPrefix.length());
        if (!StringUtils.hasText(objectKey)) {
            throw new InvalidRequestException("Markdown 图片 objectKey 不能为空");
        }
        if (objectKey.contains("..") || objectKey.startsWith("/") || objectKey.contains("\\")) {
            throw new InvalidRequestException("Markdown 图片 objectKey 非法: " + objectKey);
        }
        int queryIndex = objectKey.indexOf('?');
        if (queryIndex >= 0) {
            objectKey = objectKey.substring(0, queryIndex);
        }
        return new MdImageReference(normalizedUrl, objectKey);
    }

    private void validateUri(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new InvalidRequestException("Markdown 图片地址必须是完整 MinIO URL: " + imageUrl);
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Markdown 图片地址不是合法 URL: " + imageUrl);
        }
    }

    private String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.strip();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

