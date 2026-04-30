package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsWord;

import java.util.List;
import java.util.UUID;

/**
 * 雅思单词 Service 接口
 */
public interface IeltsWordService {

    /**
     * 分页查询单词列表
     *
     * @param difficulty 难度筛选（可选）
     * @param wordList   词表筛选（AWL/GSL/IELTS，可选）
     * @param topicTags  话题标签筛选（可选）
     * @param page       页码（1起）
     * @param size       每页条数
     * @return 分页结果
     */
    PageResponse<IeltsWord> listWords(Integer difficulty, String wordList, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询单词
     *
     * @param id 单词 ID
     * @return 单词详情
     */
    IeltsWord getById(UUID id);

    /**
     * 新增单词
     *
     * @param word 单词信息
     * @return 创建后的单词
     */
    IeltsWord create(IeltsWord word);

    /**
     * 更新单词
     *
     * @param id   单词 ID
     * @param word 更新内容
     * @return 更新后的单词
     */
    IeltsWord update(UUID id, IeltsWord word);

    /**
     * 删除单词
     *
     * @param id 单词 ID
     */
    void delete(UUID id);

    /**
     * 批量导入单词
     *
     * @param words 单词列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsWord> words);
}
