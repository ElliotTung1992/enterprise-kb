package com.enterprise.kb.search.controller;

import com.enterprise.kb.common.dto.ApiResponse;
import com.enterprise.kb.common.exception.KbException;
import com.enterprise.kb.common.util.SecurityUtils;
import com.enterprise.kb.search.model.EvalCase;
import com.enterprise.kb.search.service.EvalCaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 评估用例管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/eval-cases")
@RequiredArgsConstructor
public class EvalCaseController {

    private final EvalCaseService evalCaseService;
    private final ObjectMapper objectMapper;

    /**
     * 查询评估用例列表。
     *
     * @param dataset  数据集，可为空
     * @param caseType 类型，可为空
     * @param enabled  是否启用，可为空
     * @param limit    返回条数
     * @return 用例列表
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<EvalCase>>> list(
            @RequestParam(required = false) String dataset,
            @RequestParam(required = false) String caseType,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(evalCaseService.listCases(dataset, caseType, enabled, limit)));
    }

    /**
     * 查询评估用例详情。
     *
     * @param id 用例 ID
     * @return 用例
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<EvalCase>> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(findCase(id)));
    }

    /**
     * 创建评估用例。
     *
     * @param evalCase 用例实体
     * @return 已创建用例
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<EvalCase>> create(@RequestBody EvalCase evalCase) {
        evalCase.setCreatedBy(SecurityUtils.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(evalCaseService.create(evalCase)));
    }

    /**
     * 更新评估用例。
     *
     * @param id       用例 ID
     * @param evalCase 用例实体
     * @return 已更新用例
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<EvalCase>> update(@PathVariable UUID id,
                                                        @RequestBody EvalCase evalCase) {
        findCase(id);
        evalCase.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(evalCaseService.update(evalCase)));
    }

    /**
     * 导入 JSONL 评估用例。
     *
     * @param jsonl JSONL 文本
     * @return 导入结果
     */
    @PostMapping(value = "/import-jsonl", consumes = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<ImportResult>> importJsonl(@RequestBody String jsonl) {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        String[] lines = jsonl.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                EvalCase evalCase = objectMapper.readValue(line, EvalCase.class);
                evalCase.setId(evalCase.getId() != null ? evalCase.getId() : UUID.randomUUID());
                evalCase.setCreatedBy(SecurityUtils.getCurrentUserId());
                if (evalCase.getCreatedAt() == null) {
                    evalCase.setCreatedAt(Instant.now());
                }
                evalCaseService.create(evalCase);
                imported++;
            } catch (Exception e) {
                errors.add("第 " + (i + 1) + " 行导入失败：" + e.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(new ImportResult(imported, errors)));
    }

    /**
     * 导出 JSONL 评估用例。
     *
     * @param dataset 数据集
     * @return JSONL 文本
     */
    @GetMapping(value = "/export-jsonl", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SYSTEM_ADMIN')")
    public ResponseEntity<String> exportJsonl(@RequestParam(required = false) String dataset) {
        List<EvalCase> cases = evalCaseService.listCases(dataset, null, null, 500);
        StringBuilder sb = new StringBuilder();
        for (EvalCase evalCase : cases) {
            try {
                sb.append(objectMapper.writeValueAsString(evalCase)).append('\n');
            } catch (Exception e) {
                throw new KbException("导出 JSONL 失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=eval-cases.jsonl")
                .body(sb.toString());
    }

    private EvalCase findCase(UUID id) {
        return evalCaseService.findById(id)
                .orElseThrow(() -> new KbException("评估用例不存在", HttpStatus.NOT_FOUND));
    }

    public record ImportResult(int imported, List<String> errors) {}
}
