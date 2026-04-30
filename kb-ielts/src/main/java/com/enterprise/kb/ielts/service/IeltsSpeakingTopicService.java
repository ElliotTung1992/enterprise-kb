package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsSpeakingTopic;

import java.util.List;
import java.util.UUID;

/**
 * 雅思口语话题 Service 接口
 */
public interface IeltsSpeakingTopicService {

    /**
     * 分页查询口语话题列表
     *
     * @param difficulty 难度筛选（可选）
     * @param part       Part 筛选（1/2/3，可选）
     * @param topicTags  话题标签（可选）
     * @param page       页码（1起）
     * @param size       每页条数
     * @return 分页结果
     */
    PageResponse<IeltsSpeakingTopic> listTopics(Integer difficulty, Integer part, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询口语话题
     *
     * @param id 话题 ID
     * @return 口语话题详情
     */
    IeltsSpeakingTopic getById(UUID id);

    /**
     * 新增口语话题
     *
     * @param topic 话题信息
     * @return 创建后的话题
     */
    IeltsSpeakingTopic create(IeltsSpeakingTopic topic);

    /**
     * 更新口语话题
     *
     * @param id    话题 ID
     * @param topic 更新内容
     * @return 更新后的话题
     */
    IeltsSpeakingTopic update(UUID id, IeltsSpeakingTopic topic);

    /**
     * 删除口语话题
     *
     * @param id 话题 ID
     */
    void delete(UUID id);

    /**
     * 批量导入口语话题
     *
     * @param topics 话题列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsSpeakingTopic> topics);
}
