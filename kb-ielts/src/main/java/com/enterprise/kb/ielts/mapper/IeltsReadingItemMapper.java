package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsReadingItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsReadingItemMapper {

    Optional<IeltsReadingItem> findById(@Param("id") UUID id);

    List<IeltsReadingItem> findAll(@Param("difficulty") Integer difficulty,
                                   @Param("trainingType") String trainingType,
                                   @Param("questionType") String questionType,
                                   @Param("topicTags") String topicTags);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("trainingType") String trainingType,
                  @Param("questionType") String questionType,
                  @Param("topicTags") String topicTags);

    int insert(IeltsReadingItem item);

    int update(IeltsReadingItem item);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsReadingItem> list);
}
