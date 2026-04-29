package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsDailyPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Mapper
public interface IeltsDailyPlanMapper {

    Optional<IeltsDailyPlan> findByPlanDate(@Param("planDate") LocalDate planDate);

    /** 查询最近 N 天的每日计划 */
    List<IeltsDailyPlan> findRecent(@Param("limit") int limit);

    int insert(IeltsDailyPlan plan);

    int update(IeltsDailyPlan plan);
}
