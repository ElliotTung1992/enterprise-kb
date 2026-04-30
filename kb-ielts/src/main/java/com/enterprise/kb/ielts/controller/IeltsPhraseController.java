package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsPhrase;
import com.enterprise.kb.ielts.service.IeltsPhraseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思短语管理接口
 */
@RestController
@RequestMapping("/api/ielts/phrases")
@RequiredArgsConstructor
public class IeltsPhraseController {

    private final IeltsPhraseService phraseService;

    /**
     * 分页查询短语列表
     *
     * @param difficulty 难度筛选
     * @param category   类型筛选
     * @param topicTags  话题标签
     * @param page       页码
     * @param size       每页条数
     * @return 分页短语列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsPhrase>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String topicTags,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(phraseService.listPhrases(difficulty, category, topicTags, studyStatus, page, size));
    }

    /**
     * 查询短语详情
     *
     * @param id 短语 ID
     * @return 短语详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsPhrase> getById(@PathVariable UUID id) {
        return ApiResponse.ok(phraseService.getById(id));
    }

    /**
     * 新增短语
     *
     * @param phrase 短语信息
     * @return 创建后的短语
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsPhrase> create(@RequestBody IeltsPhrase phrase) {
        return ApiResponse.ok(phraseService.create(phrase), "短语创建成功");
    }

    /**
     * 更新短语
     *
     * @param id     短语 ID
     * @param phrase 更新信息
     * @return 更新后的短语
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsPhrase> update(@PathVariable UUID id, @RequestBody IeltsPhrase phrase) {
        return ApiResponse.ok(phraseService.update(id, phrase), "短语更新成功");
    }

    /**
     * 删除短语
     *
     * @param id 短语 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        phraseService.delete(id);
        return ApiResponse.ok(null, "短语删除成功");
    }

    /**
     * 批量导入短语
     *
     * @param phrases 短语列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsPhrase> phrases) {
        int count = phraseService.batchImport(phrases);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
