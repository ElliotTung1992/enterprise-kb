package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsGrammarExercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsGrammarExerciseMapper {

    Optional<IeltsGrammarExercise> findById(@Param("id") UUID id);

    List<IeltsGrammarExercise> findAll(@Param("difficulty") Integer difficulty,
                                       @Param("questionType") String questionType,
                                       @Param("grammarPointId") UUID grammarPointId,
                                       @Param("studyStatus") String studyStatus);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("questionType") String questionType,
                  @Param("grammarPointId") UUID grammarPointId,
                  @Param("studyStatus") String studyStatus);

    List<IeltsGrammarExercise> findByGrammarPointId(@Param("grammarPointId") UUID grammarPointId);

    int insert(IeltsGrammarExercise exercise);

    int update(IeltsGrammarExercise exercise);

    int deleteById(@Param("id") UUID id);


    /** 取 limit 条尚无学习记录的新内容，按 difficulty、id 排序，用于每日计划补充新学项 */
    List<IeltsGrammarExercise> findNewContent(@Param("limit") int limit);
    int batchInsert(@Param("list") List<IeltsGrammarExercise> list);
}
