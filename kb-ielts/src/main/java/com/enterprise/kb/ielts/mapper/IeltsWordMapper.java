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

    Optional<IeltsWord> findByWord(@Param("word") String word);

    List<IeltsWord> findAll(@Param("difficulty") Integer difficulty,
                            @Param("wordList") String wordList,
                            @Param("topicTags") String topicTags,
                            @Param("studyStatus") String studyStatus);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("wordList") String wordList,
                  @Param("topicTags") String topicTags,
                            @Param("studyStatus") String studyStatus);

    int insert(IeltsWord word);

    int update(IeltsWord word);

    int deleteById(@Param("id") UUID id);


    /** 取 limit 条尚无学习记录的新内容，按 difficulty、id 排序，用于每日计划补充新学项 */
    List<IeltsWord> findNewContent(@Param("limit") int limit);
    int batchInsert(@Param("list") List<IeltsWord> list);

}
