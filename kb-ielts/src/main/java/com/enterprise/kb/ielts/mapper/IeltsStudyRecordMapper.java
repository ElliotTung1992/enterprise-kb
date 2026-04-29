package com.enterprise.kb.ielts.mapper;

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

    /** 查询今日到期需复习的记录 */
    List<IeltsStudyRecord> findDueForReview(@Param("today") LocalDate today);

    List<IeltsStudyRecord> findByStatus(@Param("status") String status);

    long countByStatus(@Param("status") String status);

    long countAll();

    int insert(IeltsStudyRecord record);

    int update(IeltsStudyRecord record);
}
