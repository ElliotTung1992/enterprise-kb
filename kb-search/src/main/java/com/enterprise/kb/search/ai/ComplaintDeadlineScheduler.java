package com.enterprise.kb.search.ai;

import com.enterprise.kb.search.mapper.ComplaintPlanMapper;
import com.enterprise.kb.search.model.ComplaintPlan;
import com.enterprise.kb.search.service.ComplaintWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 投诉升级超时检查调度器。
 * <p>每分钟扫描一次 EXECUTING 状态且 next_check_at 已到期的计划，
 * 触发重规划或升级高级专员。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintDeadlineScheduler {

    private final ComplaintPlanMapper complaintPlanMapper;
    private final ComplaintWorkflowService complaintWorkflowService;

    @Scheduled(fixedDelay = 60_000)
    public void checkDeadlines() {
        List<ComplaintPlan> overdue = complaintPlanMapper.findExecutingPastDeadline(Instant.now());
        if (overdue.isEmpty()) {
            return;
        }
        log.info("发现 {} 个超时投诉处理计划，开始触发重规划", overdue.size());
        for (ComplaintPlan plan : overdue) {
            try {
                log.info("触发重规划：planId={}，complaintId={}，nextCheckAt={}",
                        plan.getId(), plan.getComplaintId(), plan.getNextCheckAt());
                complaintWorkflowService.triggerReplan(plan.getId());
            } catch (Exception e) {
                log.error("重规划失败：planId={}，原因：{}", plan.getId(), e.getMessage(), e);
            }
        }
    }
}
