package com.enterprise.kb.document.service;

import com.enterprise.kb.common.constants.DocumentStatus;
import com.enterprise.kb.document.dto.MdDocumentDto;
import com.github.pagehelper.PageInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Markdown 文档服务。
 */
public interface MdDocumentService {

    /**
     * 上传 Markdown 文档并异步触发结构化入库。
     *
     * @param spaceId 空间 ID
     * @param file    Markdown 文件
     * @param userId  上传用户 ID
     * @return 文档 DTO
     */
    MdDocumentDto uploadDocument(UUID spaceId, MultipartFile file, UUID userId);

    /**
     * 查询 Markdown 文档详情。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     * @return 文档 DTO
     */
    MdDocumentDto getDocument(UUID spaceId, UUID documentId);

    /**
     * 分页查询 Markdown 文档。
     *
     * @param spaceId 空间 ID
     * @param status  状态过滤
     * @param keyword 标题关键词
     * @param page    页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageInfo<MdDocumentDto> listDocuments(UUID spaceId, DocumentStatus status, String keyword, int page, int size);

    /**
     * 删除 Markdown 文档及其 parent/child/向量数据。
     *
     * @param spaceId    空间 ID
     * @param documentId 文档 ID
     */
    void deleteDocument(UUID spaceId, UUID documentId);
}
