package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsPronunciationPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface IeltsPronunciationPointMapper {

    Optional<IeltsPronunciationPoint> findById(@Param("id") UUID id);

    List<IeltsPronunciationPoint> findAll(@Param("difficulty") Integer difficulty,
                                          @Param("category") String category);

    long countAll(@Param("difficulty") Integer difficulty,
                  @Param("category") String category);

    int insert(IeltsPronunciationPoint point);

    int update(IeltsPronunciationPoint point);

    int deleteById(@Param("id") UUID id);

    int batchInsert(@Param("list") List<IeltsPronunciationPoint> list);
}
