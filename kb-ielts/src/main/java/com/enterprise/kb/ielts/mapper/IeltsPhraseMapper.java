package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsPhrase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsPhraseMapper {

    Optional<IeltsPhrase> findById(@Param("id") UUID id);

    List<IeltsPhrase> findAll(@Param("difficulty") Integer difficulty,
                              @Param("category") String category,
                              @Param("topicTags") String topicTags);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("category") String category,
                  @Param("topicTags") String topicTags);

    int insert(IeltsPhrase phrase);

    int update(IeltsPhrase phrase);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsPhrase> list);
}
