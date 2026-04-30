package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsWritingTask;
import com.enterprise.kb.ielts.service.IeltsWritingTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思写作任务管理接口
 */
@RestController
@RequestMapping("/api/ielts/writing-tasks")
@RequiredArgsConstructor
public class IeltsWritingTaskController {

    private final IeltsWritingTaskService taskService;

    /**
     * 分页查询写作任务列表
     *
     * @param difficulty   难度筛选
     * @param taskNumber   Task 编号（1或2）
     * @param trainingType 考试类型（ACADEMIC/GENERAL）
     * @param topicTags    话题标签
     * @param page         页码
     * @param size         每页条数
     * @return 分页写作任务列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsWritingTask>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer taskNumber,
            @RequestParam(required = false) String trainingType,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(taskService.listTasks(difficulty, taskNumber, trainingType, topicTags, studyStatus, page, size));
    }

    /**
     * 查询写作任务详情
     *
     * @param id 任务 ID
     * @return 写作任务详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsWritingTask> getById(@PathVariable UUID id) {
        return ApiResponse.ok(taskService.getById(id));
    }

    /**
     * 新增写作任务
     *
     * @param task 任务信息
     * @return 创建后的任务
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsWritingTask> create(@RequestBody IeltsWritingTask task) {
        return ApiResponse.ok(taskService.create(task), "写作任务创建成功");
    }

    /**
     * 更新写作任务
     *
     * @param id   任务 ID
     * @param task 更新信息
     * @return 更新后的任务
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsWritingTask> update(@PathVariable UUID id, @RequestBody IeltsWritingTask task) {
        return ApiResponse.ok(taskService.update(id, task), "写作任务更新成功");
    }

    /**
     * 删除写作任务
     *
     * @param id 任务 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        taskService.delete(id);
        return ApiResponse.ok(null, "写作任务删除成功");
    }

    /**
     * 批量导入写作任务
     *
     * @param tasks 任务列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsWritingTask> tasks) {
        int count = taskService.batchImport(tasks);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
