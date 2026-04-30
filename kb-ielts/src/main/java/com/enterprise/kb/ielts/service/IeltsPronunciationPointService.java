package com.enterprise.kb.ielts.service;

import com.enterprise.kb.common.dto.PageResponse;
import com.enterprise.kb.ielts.model.IeltsPronunciationPoint;

import java.util.List;
import java.util.UUID;

/**
 * 雅思发音要点 Service 接口
 */
public interface IeltsPronunciationPointService {

    /**
     * 分页查询发音要点列表
     *
     * @param difficulty 难度筛选（可选）
     * @param category   分类筛选（可选）
     * @param page       页码（1起）
     * @param size       每页条数
     * @return 分页结果
     */
    PageResponse<IeltsPronunciationPoint> listPoints(Integer difficulty, String category, String studyStatus, int page, int size);

    /**
     * 按 ID 查询发音要点
     *
     * @param id 要点 ID
     * @return 发音要点详情
     */
    IeltsPronunciationPoint getById(UUID id);

    /**
     * 新增发音要点
     *
     * @param point 发音要点信息
     * @return 创建后的发音要点
     */
    IeltsPronunciationPoint create(IeltsPronunciationPoint point);

    /**
     * 更新发音要点
     *
     * @param id    要点 ID
     * @param point 更新内容
     * @return 更新后的发音要点
     */
    IeltsPronunciationPoint update(UUID id, IeltsPronunciationPoint point);

    /**
     * 删除发音要点
     *
     * @param id 要点 ID
     */
    void delete(UUID id);

    /**
     * 批量导入发音要点
     *
     * @param points 发音要点列表
     * @return 成功导入数量
     */
    int batchImport(List<IeltsPronunciationPoint> points);
}
