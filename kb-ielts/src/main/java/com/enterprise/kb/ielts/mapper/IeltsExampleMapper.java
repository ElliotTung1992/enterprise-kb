package com.enterprise.kb.ielts.mapper;

import com.enterprise.kb.ielts.model.IeltsExample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface IeltsExampleMapper {

    List<IeltsExample> findByContent(@Param("contentType") String contentType,
                                     @Param("contentId") UUID contentId);

    int insert(IeltsExample example);

    int batchInsert(@Param("list") List<IeltsExample> list);

    int deleteByContent(@Param("contentType") String contentType,
                        @Param("contentId") UUID contentId);
}
