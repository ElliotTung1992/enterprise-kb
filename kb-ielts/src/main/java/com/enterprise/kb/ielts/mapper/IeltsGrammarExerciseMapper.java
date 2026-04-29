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
                                       @Param("grammarPointId") UUID grammarPointId);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("questionType") String questionType,
                  @Param("grammarPointId") UUID grammarPointId);

    List<IeltsGrammarExercise> findByGrammarPointId(@Param("grammarPointId") UUID grammarPointId);

    int insert(IeltsGrammarExercise exercise);

    int update(IeltsGrammarExercise exercise);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsGrammarExercise> list);
}
