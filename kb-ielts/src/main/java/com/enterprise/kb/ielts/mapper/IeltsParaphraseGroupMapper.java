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
                                       @Param("topicTags") String topicTags,
                            @Param("studyStatus") String studyStatus);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("topicTags") String topicTags,
                            @Param("studyStatus") String studyStatus);

    int insert(IeltsParaphraseGroup group);

    int update(IeltsParaphraseGroup group);

    int deleteById(@Param("id") UUID id);


    /** 取 limit 条尚无学习记录的新内容，按 difficulty、id 排序，用于每日计划补充新学项 */
    List<IeltsParaphraseGroup> findNewContent(@Param("limit") int limit);
    int batchInsert(@Param("list") List<IeltsParaphraseGroup> list);
}
