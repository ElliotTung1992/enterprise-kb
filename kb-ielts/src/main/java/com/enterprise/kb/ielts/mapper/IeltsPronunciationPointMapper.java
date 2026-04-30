package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsPronunciationPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsPronunciationPointMapper {

    Optional<IeltsPronunciationPoint> findById(@Param("id") UUID id);

    List<IeltsPronunciationPoint> findAll(@Param("difficulty") Integer difficulty,
                                          @Param("category") String category,
                                          @Param("studyStatus") String studyStatus);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("category") String category,
                  @Param("studyStatus") String studyStatus);

    int insert(IeltsPronunciationPoint point);

    int update(IeltsPronunciationPoint point);

    int deleteById(@Param("id") UUID id);


    /** 取 limit 条尚无学习记录的新内容，按 difficulty、id 排序，用于每日计划补充新学项 */
    List<IeltsPronunciationPoint> findNewContent(@Param("limit") int limit);
    int batchInsert(@Param("list") List<IeltsPronunciationPoint> list);
}
