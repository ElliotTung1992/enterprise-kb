package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsListeningItem;

import java.util.List;
import java.util.UUID;

/**
 * 雅思听力练习 Service 接口
 */
public interface IeltsListeningItemService {

    /**
     * 分页查询听力练习列表
     *
     * @param difficulty   难度筛选（可选）
     * @param section      Section 筛选（1-4，可选）
     * @param questionType 题型筛选（可选）
     * @param topicTags    话题标签（可选）
     * @param page         页码（1起）
     * @param size         每页条数
     * @return 分页结果
     */
    PageResponse<IeltsListeningItem> listItems(Integer difficulty, Integer section, String questionType, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询听力练习
     *
     * @param id 练习 ID
     * @return 听力练习详情
     */
    IeltsListeningItem getById(UUID id);

    /**
     * 新增听力练习
     *
     * @param item 听力练习信息
     * @return 创建后的练习
     */
    IeltsListeningItem create(IeltsListeningItem item);

    /**
     * 更新听力练习
     *
     * @param id   练习 ID
     * @param item 更新内容
     * @return 更新后的练习
     */
    IeltsListeningItem update(UUID id, IeltsListeningItem item);

    /**
     * 删除听力练习
     *
     * @param id 练习 ID
     */
    void delete(UUID id);

    /**
     * 批量导入听力练习
     *
     * @param items 练习列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsListeningItem> items);
}
