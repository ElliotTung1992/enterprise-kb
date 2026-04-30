package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsReadingItem;
import com.enterprise.kb.ielts.service.IeltsReadingItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思阅读练习管理接口
 */
@RestController
@RequestMapping("/api/ielts/reading-items")
@RequiredArgsConstructor
public class IeltsReadingItemController {

    private final IeltsReadingItemService itemService;

    /**
     * 分页查询阅读练习列表
     *
     * @param difficulty   难度筛选
     * @param trainingType 考试类型（ACADEMIC/GENERAL）
     * @param questionType 题型筛选
     * @param topicTags    话题标签
     * @param page         页码
     * @param size         每页条数
     * @return 分页阅读练习列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsReadingItem>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String trainingType,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(itemService.listItems(difficulty, trainingType, questionType, topicTags, studyStatus, page, size));
    }

    /**
     * 查询阅读练习详情
     *
     * @param id 练习 ID
     * @return 阅读练习详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsReadingItem> getById(@PathVariable UUID id) {
        return ApiResponse.ok(itemService.getById(id));
    }

    /**
     * 新增阅读练习
     *
     * @param item 练习信息
     * @return 创建后的练习
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsReadingItem> create(@RequestBody IeltsReadingItem item) {
        return ApiResponse.ok(itemService.create(item), "阅读练习创建成功");
    }

    /**
     * 更新阅读练习
     *
     * @param id   练习 ID
     * @param item 更新信息
     * @return 更新后的练习
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsReadingItem> update(@PathVariable UUID id, @RequestBody IeltsReadingItem item) {
        return ApiResponse.ok(itemService.update(id, item), "阅读练习更新成功");
    }

    /**
     * 删除阅读练习
     *
     * @param id 练习 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        itemService.delete(id);
        return ApiResponse.ok(null, "阅读练习删除成功");
    }

    /**
     * 批量导入阅读练习
     *
     * @param items 练习列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsReadingItem> items) {
        int count = itemService.batchImport(items);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
