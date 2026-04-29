package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsSpeakingTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsSpeakingTopicMapper {

    Optional<IeltsSpeakingTopic> findById(@Param("id") UUID id);

    List<IeltsSpeakingTopic> findAll(@Param("difficulty") Integer difficulty,
                                     @Param("part") Integer part,
                                     @Param("topicTags") String topicTags);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("part") Integer part,
                  @Param("topicTags") String topicTags);

    int insert(IeltsSpeakingTopic topic);

    int update(IeltsSpeakingTopic topic);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsSpeakingTopic> list);
}
