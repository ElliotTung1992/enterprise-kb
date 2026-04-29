package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsWordMapper {

    Optional<IeltsWord> findById(@Param("id") UUID id);

    List<IeltsWord> findAll(@Param("difficulty") Integer difficulty,
                            @Param("wordList") String wordList,
                            @Param("topicTags") String topicTags);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("wordList") String wordList,
                  @Param("topicTags") String topicTags);

    int insert(IeltsWord word);

    int update(IeltsWord word);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsWord> list);
}
