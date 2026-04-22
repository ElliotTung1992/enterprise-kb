package com.enterprise.kb.common.dto;

import com.github.pagehelper.PageInfo;
import lombok.Getter;

import java.util.List;

/**
 * 分页响应 DTO，封装 PageHelper 的 PageInfo 为统一响应格式。
 *
 * @param <T> 数据类型
 */
@Getter
public class PageResponse<T> {

    private final List<T> content;
    private final long totalElements;
    private final int totalPages;
    private final int page;
    private final int size;

    /**
     * 从 PageHelper PageInfo 构建分页响应
     *
     * @param pageInfo PageHelper 分页信息
     */
    public PageResponse(PageInfo<T> pageInfo) {
        this.content = pageInfo.getList();
        this.totalElements = pageInfo.getTotal();
        this.totalPages = pageInfo.getPages();
        this.page = pageInfo.getPageNum() - 1; // Convert back to 0-based
        this.size = pageInfo.getPageSize();
    }

    /**
     * 静态工厂方法，从 PageInfo 创建 PageResponse
     *
     * @param pageInfo PageHelper 分页信息
     * @param <T>      数据类型
     * @return 分页响应对象
     */
    public static <T> PageResponse<T> of(PageInfo<T> pageInfo) {
        return new PageResponse<>(pageInfo);
    }
}
