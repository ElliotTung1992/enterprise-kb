package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.search.model.EvalRun;
import com.enterprise.kb.search.model.EvalRunResult;
import com.enterprise.kb.search.service.EvalReplayService;
import com.enterprise.kb.search.service.EvalRunService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
// import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 评估运行管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/eval-runs")
@RequiredArgsConstructor
public class EvalRunController {

    private final EvalReplayService evalReplayService;
    private final EvalRunService evalRunService;

    /**
     * 运行指定数据集的离线评估。
     *
     * @param req 运行请求
     * @return 运行记录
     */
    @PostMapping
    // @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<EvalRun>> run(@RequestBody RunEvalRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(evalReplayService.runDataset(req.dataset())));
    }

    /**
     * 查询评估运行详情。
     *
     * @param id 运行 ID
     * @return 运行与结果列表
     */
    @GetMapping("/{id}")
    // @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable UUID id) {
        EvalRun run = evalRunService.findById(id)
                .orElseThrow(() -> new KbException("评估运行不存在", HttpStatus.NOT_FOUND));
        List<EvalRunResult> results = evalRunService.listResults(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("run", run);
        body.put("results", results);
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    public record RunEvalRequest(String dataset) {}
}
