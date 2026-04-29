package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsReviewLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Mapper
public interface IeltsReviewLogMapper {

    int insert(IeltsReviewLog log);

    List<IeltsReviewLog> findByRecordId(@Param("recordId") UUID recordId);

    /** 统计指定日期的复习次数 */
    long countByDate(@Param("date") LocalDate date);
}
