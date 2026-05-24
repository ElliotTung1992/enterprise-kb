package com.enterprise.kb.document.service;

import java.nio.file.Path;

/**
 * 文档对象存储服务，负责将原始文件和视觉资产写入对象存储。
 */
public interface DocumentObjectStorageService {

    /**
     * 上传本地文件到对象存储。
     *
     * @param objectKey   对象 key
     * @param file        本地文件路径
     * @param contentType MIME 类型
     * @return 对象 key
     */
    String uploadFile(String objectKey, Path file, String contentType);

    /**
     * 删除对象存储中的文件。
     *
     * @param objectKey 对象 key
     */
    void deleteFile(String objectKey);

    /**
     * 生成对象的短期访问 URL。
     *
     * @param objectKey     对象 key
     * @param expirySeconds 有效期（秒）
     * @return 预签名 URL
     */
    String presignedGetUrl(String objectKey, int expirySeconds);
}
