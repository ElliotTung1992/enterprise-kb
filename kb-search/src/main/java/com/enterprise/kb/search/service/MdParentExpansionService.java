package com.enterprise.kb.search.service;

import com.enterprise.kb.search.dto.SearchHit;

import java.util.List;

/**
 * Markdown parent 展开服务。
 */
public interface MdParentExpansionService {

    /**
     * 将 child 命中展开为完整 parent section。
     *
     * @param childHits child 命中列表
     * @return parent 级上下文
     */
    List<SearchHit> expand(List<SearchHit> childHits);
}
