package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsWritingTask;

import java.util.List;
import java.util.UUID;

/**
 * 雅思写作任务 Service 接口
 */
public interface IeltsWritingTaskService {

    /**
     * 分页查询写作任务列表
     *
     * @param difficulty   难度筛选（可选）
     * @param taskNumber   Task 编号（1或2，可选）
     * @param trainingType 考试类型（ACADEMIC/GENERAL，可选）
     * @param topicTags    话题标签（可选）
     * @param page         页码（1起）
     * @param size         每页条数
     * @return 分页结果
     */
    PageResponse<IeltsWritingTask> listTasks(Integer difficulty, Integer taskNumber, String trainingType, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询写作任务
     *
     * @param id 任务 ID
     * @return 写作任务详情
     */
    IeltsWritingTask getById(UUID id);

    /**
     * 新增写作任务
     *
     * @param task 写作任务信息
     * @return 创建后的任务
     */
    IeltsWritingTask create(IeltsWritingTask task);

    /**
     * 更新写作任务
     *
     * @param id   任务 ID
     * @param task 更新内容
     * @return 更新后的任务
     */
    IeltsWritingTask update(UUID id, IeltsWritingTask task);

    /**
     * 删除写作任务
     *
     * @param id 任务 ID
     */
    void delete(UUID id);

    /**
     * 批量导入写作任务
     *
     * @param tasks 任务列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsWritingTask> tasks);
}
