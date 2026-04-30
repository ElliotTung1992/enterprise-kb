package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsParaphraseGroup;
import com.enterprise.kb.ielts.service.IeltsParaphraseGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思同义替换组管理接口
 */
@RestController
@RequestMapping("/api/ielts/paraphrase-groups")
@RequiredArgsConstructor
public class IeltsParaphraseGroupController {

    private final IeltsParaphraseGroupService groupService;

    /**
     * 分页查询同义替换组列表
     *
     * @param difficulty 难度筛选
     * @param topicTags  话题标签
     * @param page       页码
     * @param size       每页条数
     * @return 分页替换组列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsParaphraseGroup>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(groupService.listGroups(difficulty, topicTags, studyStatus, page, size));
    }

    /**
     * 查询替换组详情
     *
     * @param id 替换组 ID
     * @return 替换组详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsParaphraseGroup> getById(@PathVariable UUID id) {
        return ApiResponse.ok(groupService.getById(id));
    }

    /**
     * 新增替换组
     *
     * @param group 替换组信息
     * @return 创建后的替换组
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsParaphraseGroup> create(@RequestBody IeltsParaphraseGroup group) {
        return ApiResponse.ok(groupService.create(group), "替换组创建成功");
    }

    /**
     * 更新替换组
     *
     * @param id    替换组 ID
     * @param group 更新信息
     * @return 更新后的替换组
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsParaphraseGroup> update(@PathVariable UUID id, @RequestBody IeltsParaphraseGroup group) {
        return ApiResponse.ok(groupService.update(id, group), "替换组更新成功");
    }

    /**
     * 删除替换组
     *
     * @param id 替换组 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        groupService.delete(id);
        return ApiResponse.ok(null, "替换组删除成功");
    }

    /**
     * 批量导入替换组
     *
     * @param groups 替换组列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsParaphraseGroup> groups) {
        int count = groupService.batchImport(groups);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
