package com.enterprise.kb.ielts.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsGrammarExercise;
import com.enterprise.kb.ielts.service.IeltsGrammarExerciseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 雅思语法练习题管理接口
 */
@RestController
@RequestMapping("/api/ielts/grammar-exercises")
@RequiredArgsConstructor
public class IeltsGrammarExerciseController {

    private final IeltsGrammarExerciseService exerciseService;

    /**
     * 分页查询练习题列表
     *
     * @param difficulty     难度筛选
     * @param questionType   题型筛选
     * @param grammarPointId 关联语法要点 ID
     * @param page           页码
     * @param size           每页条数
     * @return 分页练习题列表
     */
    @GetMapping
    public ApiResponse<PageResponse<IeltsGrammarExercise>> list(
            @RequestParam(required = false) Integer difficulty,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) UUID grammarPointId,
            @RequestParam(required = false) String studyStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(exerciseService.listExercises(difficulty, questionType, grammarPointId, studyStatus, page, size));
    }

    /**
     * 查询练习题详情
     *
     * @param id 练习题 ID
     * @return 练习题详情
     */
    @GetMapping("/{id}")
    public ApiResponse<IeltsGrammarExercise> getById(@PathVariable UUID id) {
        return ApiResponse.ok(exerciseService.getById(id));
    }

    /**
     * 查询某语法要点下的所有练习题
     *
     * @param grammarPointId 语法要点 ID
     * @return 练习题列表
     */
    @GetMapping("/by-grammar-point/{grammarPointId}")
    public ApiResponse<List<IeltsGrammarExercise>> listByGrammarPoint(@PathVariable UUID grammarPointId) {
        return ApiResponse.ok(exerciseService.listByGrammarPoint(grammarPointId));
    }

    /**
     * 新增练习题
     *
     * @param exercise 练习题信息
     * @return 创建后的练习题
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IeltsGrammarExercise> create(@RequestBody IeltsGrammarExercise exercise) {
        return ApiResponse.ok(exerciseService.create(exercise), "练习题创建成功");
    }

    /**
     * 更新练习题
     *
     * @param id       练习题 ID
     * @param exercise 更新信息
     * @return 更新后的练习题
     */
    @PutMapping("/{id}")
    public ApiResponse<IeltsGrammarExercise> update(@PathVariable UUID id, @RequestBody IeltsGrammarExercise exercise) {
        return ApiResponse.ok(exerciseService.update(id, exercise), "练习题更新成功");
    }

    /**
     * 删除练习题
     *
     * @param id 练习题 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        exerciseService.delete(id);
        return ApiResponse.ok(null, "练习题删除成功");
    }

    /**
     * 批量导入练习题
     *
     * @param exercises 练习题列表
     * @return 导入数量
     */
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Integer>> batchImport(@RequestBody List<IeltsGrammarExercise> exercises) {
        int count = exerciseService.batchImport(exercises);
        return ApiResponse.ok(Map.of("imported", count), "批量导入完成");
    }
}
