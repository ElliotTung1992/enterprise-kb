package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsListeningItem;
import com.enterprise.kb.ielts.service.IeltsListeningItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思听力练习管理接口
 */
@RestController
@RequestMapping("/api/ielts/listening-items")
@RequiredArgsConstructor
public class IeltsListeningItemController {

    private final IeltsListeningItemService itemService;

    /**
     * 分页查询听力练习列表
     *
     * @param difficulty   难度筛选
     * @param section      Section 筛选（1-4）
     * @param questionType 题型筛选
     * @param topicTags    话题标签
     * @param page         页码
     * @param size         每页条数
     * @return 分页听力练习列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsListeningItem>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) Integer section,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(itemService.listItems(difficulty, section, questionType, topicTags, studyStatus, page, size));
    }

    /**
     * 查询听力练习详情
     *
     * @param id 练习 ID
     * @return 听力练习详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsListeningItem> getById(@PathVariable UUID id) {
        return ApiResponse.ok(itemService.getById(id));
    }

    /**
     * 新增听力练习
     *
     * @param item 练习信息
     * @return 创建后的练习
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsListeningItem> create(@RequestBody IeltsListeningItem item) {
        return ApiResponse.ok(itemService.create(item), "听力练习创建成功");
    }

    /**
     * 更新听力练习
     *
     * @param id   练习 ID
     * @param item 更新信息
     * @return 更新后的练习
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsListeningItem> update(@PathVariable UUID id, @RequestBody IeltsListeningItem item) {
        return ApiResponse.ok(itemService.update(id, item), "听力练习更新成功");
    }

    /**
     * 删除听力练习
     *
     * @param id 练习 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        itemService.delete(id);
        return ApiResponse.ok(null, "听力练习删除成功");
    }

    /**
     * 批量导入听力练习
     *
     * @param items 练习列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsListeningItem> items) {
        int count = itemService.batchImport(items);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
