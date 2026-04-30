package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsGrammarExercise;

import java.util.List;
import java.util.UUID;

/**
 * 雅思语法练习题 Service 接口
 */
public interface IeltsGrammarExerciseService {

    /**
     * 分页查询练习题列表
     *
     * @param difficulty     难度筛选（可选）
     * @param questionType   题型筛选（可选）
     * @param grammarPointId 关联语法要点 ID（可选）
     * @param page           页码（1起）
     * @param size           每页条数
     * @return 分页结果
     */
    PageResponse<IeltsGrammarExercise> listExercises(Integer difficulty, String questionType, UUID grammarPointId, String studyStatus, int page, int size);

    /**
     * 按 ID 查询练习题
     *
     * @param id 练习题 ID
     * @return 练习题详情
     */
    IeltsGrammarExercise getById(UUID id);

    /**
     * 按语法要点查询练习题
     *
     * @param grammarPointId 语法要点 ID
     * @return 练习题列表
     */
    List<IeltsGrammarExercise> listByGrammarPoint(UUID grammarPointId);

    /**
     * 新增练习题
     *
     * @param exercise 练习题信息
     * @return 创建后的练习题
     */
    IeltsGrammarExercise create(IeltsGrammarExercise exercise);

    /**
     * 更新练习题
     *
     * @param id       练习题 ID
     * @param exercise 更新内容
     * @return 更新后的练习题
     */
    IeltsGrammarExercise update(UUID id, IeltsGrammarExercise exercise);

    /**
     * 删除练习题
     *
     * @param id 练习题 ID
     */
    void delete(UUID id);

    /**
     * 批量导入练习题
     *
     * @param exercises 练习题列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsGrammarExercise> exercises);
}
