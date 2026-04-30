package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.dto.StudyPlanItem;
import com.enterprise.kb.ielts.model.IeltsStudyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsStudyRecordMapper {

    Optional<IeltsStudyRecord> findById(@Param("id") UUID id);

    Optional<IeltsStudyRecord> findByContentTypeAndContentId(@Param("contentType") String contentType,
                                                              @Param("contentId") UUID contentId);

    /** 查询今日到期需复习的记录（不含摘要） */
    List<IeltsStudyRecord> findDueForReview(@Param("today") LocalDate today);

    /**
     * 查询今日到期需复习的条目，通过 LEFT JOIN 所有内容表拼装摘要字段，
     * 用于直接构造 StudyPlanItem 列表，避免 N+1 查询。
     */
    List<StudyPlanItem> findDueItemsWithSummary(@Param("today") LocalDate today);

    List<IeltsStudyRecord> findByStatus(@Param("status") String status);

    /** 按状态查询，LEFT JOIN 所有内容表补充摘要，用于统计页列表展示 */
    List<StudyPlanItem> findByStatusWithSummary(@Param("status") String status);

    long countByStatus(@Param("status") String status);

    long countAll();

    int insert(IeltsStudyRecord record);

    int update(IeltsStudyRecord record);
}
