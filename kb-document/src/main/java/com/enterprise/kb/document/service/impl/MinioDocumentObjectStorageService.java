package com.enterprise.kb.document.service.impl;

import com.enterprise.kb.document.service.DocumentObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 基于 MinIO 的文档对象存储实现。
 */
@Slf4j
@Service
public class MinioDocumentObjectStorageService implements DocumentObjectStorageService {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioDocumentObjectStorageService(
            @Value("${enterprise.kb.storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${enterprise.kb.storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${enterprise.kb.storage.minio.secret-key:minioadmin123}") String secretKey,
            @Value("${enterprise.kb.storage.minio.bucket:kb-assets}") String bucket) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
    }

    @Override
    public String uploadFile(String objectKey, Path file, String contentType) {
        try {
            ensureBucket();
            minioClient.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .filename(file.toString())
                    .contentType(contentType)
                    .build());
            return objectKey;
        } catch (Exception e) {
            throw new IllegalStateException("上传对象到 MinIO 失败: " + objectKey, e);
        }
    }

    @Override
    public void downloadFile(String objectKey, Path target) {
        try {
            ensureBucket();
            try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build())) {
                Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("从 MinIO 下载对象失败: " + objectKey, e);
        }
    }

    @Override
    public void deleteFile(String objectKey) {
        try {
            ensureBucket();
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("删除 MinIO 对象失败: " + objectKey, e);
        }
    }

    @Override
    public String presignedGetUrl(String objectKey, int expirySeconds) {
        try {
            ensureBucket();
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("生成 MinIO 预签名 URL 失败: " + objectKey, e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created MinIO bucket {}", bucket);
        }
    }
}
