package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsParaphraseGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsParaphraseGroupMapper {

    Optional<IeltsParaphraseGroup> findById(@Param("id") UUID id);

    List<IeltsParaphraseGroup> findAll(@Param("difficulty") Integer difficulty,
                                       @Param("topicTags") String topicTags);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("topicTags") String topicTags);

    int insert(IeltsParaphraseGroup group);

    int update(IeltsParaphraseGroup group);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsParaphraseGroup> list);
}
