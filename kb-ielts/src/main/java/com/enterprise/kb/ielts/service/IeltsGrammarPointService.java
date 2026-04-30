package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsGrammarPoint;

import java.util.List;
import java.util.UUID;

/**
 * 雅思语法要点 Service 接口
 */
public interface IeltsGrammarPointService {

    /**
     * 分页查询语法要点列表
     *
     * @param difficulty 难度筛选（可选）
     * @param category   分类筛选（可选）
     * @param topicTags  话题标签（可选）
     * @param page       页码（1起）
     * @param size       每页条数
     * @return 分页结果
     */
    PageResponse<IeltsGrammarPoint> listPoints(Integer difficulty, String category, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询语法要点
     *
     * @param id 要点 ID
     * @return 语法要点详情
     */
    IeltsGrammarPoint getById(UUID id);

    /**
     * 新增语法要点
     *
     * @param point 语法要点信息
     * @return 创建后的语法要点
     */
    IeltsGrammarPoint create(IeltsGrammarPoint point);

    /**
     * 更新语法要点
     *
     * @param id    要点 ID
     * @param point 更新内容
     * @return 更新后的语法要点
     */
    IeltsGrammarPoint update(UUID id, IeltsGrammarPoint point);

    /**
     * 删除语法要点
     *
     * @param id 要点 ID
     */
    void delete(UUID id);

    /**
     * 批量导入语法要点
     *
     * @param points 语法要点列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsGrammarPoint> points);
}
