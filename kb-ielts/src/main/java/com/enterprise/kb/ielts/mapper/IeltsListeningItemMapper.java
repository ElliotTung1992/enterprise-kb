package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsListeningItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsListeningItemMapper {

    Optional<IeltsListeningItem> findById(@Param("id") UUID id);

    List<IeltsListeningItem> findAll(@Param("difficulty") Integer difficulty,
                                     @Param("section") Integer section,
                                     @Param("questionType") String questionType,
                                     @Param("topicTags") String topicTags,
                            @Param("studyStatus") String studyStatus);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("section") Integer section,
                  @Param("questionType") String questionType,
                  @Param("topicTags") String topicTags,
                            @Param("studyStatus") String studyStatus);

    int insert(IeltsListeningItem item);

    int update(IeltsListeningItem item);

    int deleteById(@Param("id") UUID id);


    /** 取 limit 条尚无学习记录的新内容，按 difficulty、id 排序，用于每日计划补充新学项 */
    List<IeltsListeningItem> findNewContent(@Param("limit") int limit);
    int batchInsert(@Param("list") List<IeltsListeningItem> list);
}
