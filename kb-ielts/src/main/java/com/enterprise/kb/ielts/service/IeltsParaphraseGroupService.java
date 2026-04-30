package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsParaphraseGroup;

import java.util.List;
import java.util.UUID;

/**
 * 雅思同义替换组 Service 接口
 */
public interface IeltsParaphraseGroupService {

    /**
     * 分页查询同义替换组列表
     *
     * @param difficulty 难度筛选（可选）
     * @param topicTags  话题标签（可选）
     * @param page       页码（1起）
     * @param size       每页条数
     * @return 分页结果
     */
    PageResponse<IeltsParaphraseGroup> listGroups(Integer difficulty, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询替换组
     *
     * @param id 替换组 ID
     * @return 替换组详情
     */
    IeltsParaphraseGroup getById(UUID id);

    /**
     * 新增替换组
     *
     * @param group 替换组信息
     * @return 创建后的替换组
     */
    IeltsParaphraseGroup create(IeltsParaphraseGroup group);

    /**
     * 更新替换组
     *
     * @param id    替换组 ID
     * @param group 更新内容
     * @return 更新后的替换组
     */
    IeltsParaphraseGroup update(UUID id, IeltsParaphraseGroup group);

    /**
     * 删除替换组
     *
     * @param id 替换组 ID
     */
    void delete(UUID id);

    /**
     * 批量导入替换组
     *
     * @param groups 替换组列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsParaphraseGroup> groups);
}
