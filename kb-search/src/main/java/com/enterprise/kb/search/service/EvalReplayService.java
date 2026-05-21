package com.enterprise.kb.search.service;

import com.enterprise.kb.search.model.EvalRun;

/**
 * 离线评估回放服务。
 */
public interface EvalReplayService {

    /**
     * 运行指定数据集的最小离线评估。
     *
     * @param dataset 数据集名称
     * @return 评估运行记录
     */
    EvalRun runDataset(String dataset);
}
