package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsWord;
import com.enterprise.kb.ielts.service.IeltsWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思单词管理接口
 */
@RestController
@RequestMapping("/api/ielts/words")
@RequiredArgsConstructor
public class IeltsWordController {

    private final IeltsWordService wordService;

    /**
     * 分页查询单词列表
     *
     * @param difficulty 难度筛选（1/2/3）
     * @param wordList   词表（AWL/GSL/IELTS）
     * @param topicTags  话题标签
     * @param page       页码（从1开始）
     * @param size       每页条数
     * @return 分页单词列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsWord>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String wordList,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(wordService.listWords(difficulty, wordList, topicTags, studyStatus, page, size));
    }

    /**
     * 查询单词详情
     *
     * @param id 单词 ID
     * @return 单词详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsWord> getById(@PathVariable UUID id) {
        return ApiResponse.ok(wordService.getById(id));
    }

    /**
     * 新增单词
     *
     * @param word 单词信息
     * @return 创建后的单词
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsWord> create(@RequestBody IeltsWord word) {
        return ApiResponse.ok(wordService.create(word), "单词创建成功");
    }

    /**
     * 更新单词
     *
     * @param id   单词 ID
     * @param word 更新信息
     * @return 更新后的单词
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsWord> update(@PathVariable UUID id, @RequestBody IeltsWord word) {
        return ApiResponse.ok(wordService.update(id, word), "单词更新成功");
    }

    /**
     * 删除单词
     *
     * @param id 单词 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        wordService.delete(id);
        return ApiResponse.ok(null, "单词删除成功");
    }

    /**
     * 批量导入单词
     *
     * @param words 单词列表（JSON 数组）
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsWord> words) {
        int count = wordService.batchImport(words);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
