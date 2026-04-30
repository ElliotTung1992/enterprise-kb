package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsPhrase;

import java.util.List;
import java.util.UUID;

/**
 * 雅思短语 Service 接口
 */
public interface IeltsPhraseService {

    /**
     * 分页查询短语列表
     *
     * @param difficulty 难度筛选（可选）
     * @param category   类型筛选（可选）
     * @param topicTags  话题标签（可选）
     * @param page       页码（1起）
     * @param size       每页条数
     * @return 分页结果
     */
    PageResponse<IeltsPhrase> listPhrases(Integer difficulty, String category, String topicTags, String studyStatus, int page, int size);

    /**
     * 按 ID 查询短语
     *
     * @param id 短语 ID
     * @return 短语详情
     */
    IeltsPhrase getById(UUID id);

    /**
     * 新增短语
     *
     * @param phrase 短语信息
     * @return 创建后的短语
     */
    IeltsPhrase create(IeltsPhrase phrase);

    /**
     * 更新短语
     *
     * @param id     短语 ID
     * @param phrase 更新内容
     * @return 更新后的短语
     */
    IeltsPhrase update(UUID id, IeltsPhrase phrase);

    /**
     * 删除短语
     *
     * @param id 短语 ID
     */
    void delete(UUID id);

    /**
     * 批量导入短语
     *
     * @param phrases 短语列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsPhrase> phrases);
}
