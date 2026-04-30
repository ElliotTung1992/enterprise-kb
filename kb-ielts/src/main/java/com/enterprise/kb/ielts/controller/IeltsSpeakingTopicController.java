package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsSpeakingTopic;
import com.enterprise.kb.ielts.service.IeltsSpeakingTopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思口语话题管理接口
 */
@RestController
@RequestMapping("/api/ielts/speaking-topics")
@RequiredArgsConstructor
public class IeltsSpeakingTopicController {

    private final IeltsSpeakingTopicService topicService;

    /**
     * 分页查询口语话题列表
     *
     * @param difficulty 难度筛选
     * @param part       Part 筛选（1/2/3）
     * @param topicTags  话题标签
     * @param page       页码
     * @param size       每页条数
     * @return 分页话题列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsSpeakingTopic>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer part,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(topicService.listTopics(difficulty, part, topicTags, studyStatus, page, size));
    }

    /**
     * 查询口语话题详情
     *
     * @param id 话题 ID
     * @return 话题详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsSpeakingTopic> getById(@PathVariable UUID id) {
        return ApiResponse.ok(topicService.getById(id));
    }

    /**
     * 新增口语话题
     *
     * @param topic 话题信息
     * @return 创建后的话题
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsSpeakingTopic> create(@RequestBody IeltsSpeakingTopic topic) {
        return ApiResponse.ok(topicService.create(topic), "话题创建成功");
    }

    /**
     * 更新口语话题
     *
     * @param id    话题 ID
     * @param topic 更新信息
     * @return 更新后的话题
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsSpeakingTopic> update(@PathVariable UUID id, @RequestBody IeltsSpeakingTopic topic) {
        return ApiResponse.ok(topicService.update(id, topic), "话题更新成功");
    }

    /**
     * 删除口语话题
     *
     * @param id 话题 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        topicService.delete(id);
        return ApiResponse.ok(null, "话题删除成功");
    }

    /**
     * 批量导入口语话题
     *
     * @param topics 话题列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsSpeakingTopic> topics) {
        int count = topicService.batchImport(topics);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
