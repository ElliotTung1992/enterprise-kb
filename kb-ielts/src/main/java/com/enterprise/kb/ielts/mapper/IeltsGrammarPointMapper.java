package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsGrammarPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsGrammarPointMapper {

    Optional<IeltsGrammarPoint> findById(@Param("id") UUID id);

    List<IeltsGrammarPoint> findAll(@Param("difficulty") Integer difficulty,
                                    @Param("category") String category,
                                    @Param("topicTags") String topicTags);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("category") String category,
                  @Param("topicTags") String topicTags);

    int insert(IeltsGrammarPoint point);

    int update(IeltsGrammarPoint point);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsGrammarPoint> list);
}
