package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsReadingItem;

import java.util.List;
import java.util.UUID;

/**
 * 雅思阅读练习 Service 接口
 */
public interface IeltsReadingItemService {

    /**
     * 分页查询阅读练习列表
     *
     * @param difficulty   难度筛选（可选）
     * @param trainingType 考试类型（ACADEMIC/GENERAL，可选）
     * @param questionType 题型筛选（可选）
     * @param topicTags    话题标签（可选）
     * @param page         页码（1起）
     * @param size         每页条数
     * @return 分页结果
     */
    PageResponse<IeltsReadingItem> listItems(Integer difficulty, String trainingType, String questionType, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询阅读练习
     *
     * @param id 练习 ID
     * @return 阅读练习详情
     */
    IeltsReadingItem getById(UUID id);

    /**
     * 新增阅读练习
     *
     * @param item 阅读练习信息
     * @return 创建后的练习
     */
    IeltsReadingItem create(IeltsReadingItem item);

    /**
     * 更新阅读练习
     *
     * @param id   练习 ID
     * @param item 更新内容
     * @return 更新后的练习
     */
    IeltsReadingItem update(UUID id, IeltsReadingItem item);

    /**
     * 删除阅读练习
     *
     * @param id 练习 ID
     */
    void delete(UUID id);

    /**
     * 批量导入阅读练习
     *
     * @param items 练习列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsReadingItem> items);
}
