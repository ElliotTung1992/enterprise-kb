package com.enterprise.kb.search.eval;

import com.enterprise.kb.search.dto.SearchHit;
import com.enterprise.kb.search.dto.SearchRequest;
import com.enterprise.kb.search.dto.SearchResponse;
import com.enterprise.kb.search.service.impl.MdKeywordSearchServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * md 关键词路 TRGM vs BM25 离线评估 harness（设计 §9）。
 *
 * <p>读取 {@code md-keyword-eval/frozen-testset.jsonl}（固定、可复现），对同一组 query
 * 分别跑 TRGM 与 BM25 两种 {@code keyword-mode}，输出 recall@k / 命中率 / MRR 对照记分卡。
 * 翻默认门控：BM25 recall@k 不劣于（目标优于）TRGM。
 *
 * <p>默认跳过（需真实 Postgres + vchord_bm25 扩展 + 已 ingest 语料 + 已跑 db/manual/md-bm25-build.sql）。
 * 手动运行：
 * <pre>
 * MD_KEYWORD_EVAL=true \
 *   MD_EVAL_DB_URL=jdbc:postgresql://127.0.0.1:5432/enterprise_kb \
 *   MD_EVAL_DB_USER=kb_user MD_EVAL_DB_PASSWORD=xxx \
 *   mvn test -pl kb-search -Dtest=MdKeywordEvalTest
 * </pre>
 *
 * <p>注意：当前仓库内 frozen-testset.jsonl 是模板示例，门控只有在运营测试同学按 README 填充
 * 真实 {@code query → relevantChildIds} 后才有统计意义。
 */
@EnabledIfEnvironmentVariable(named = "MD_KEYWORD_EVAL", matches = "true")
class MdKeywordEvalTest {

    private static final Logger log = LoggerFactory.getLogger(MdKeywordEvalTest.class);

    private static final String TESTSET = "md-keyword-eval/frozen-testset.jsonl";

    @Test
    void compareTrgmVsBm25() throws Exception {
        JdbcTemplate jdbc = buildJdbcTemplate();
        List<JsonNode> cases = loadTestset();
        assertThat(cases).as("评估集非空").isNotEmpty();

        Score trgm = scoreMode(jdbc, cases, "TRGM");
        Score bm25 = scoreMode(jdbc, cases, "BM25");

        log.info("\n{}", renderScorecard(cases.size(), trgm, bm25));

        // 翻默认门控：BM25 在种子集上 recall@k 不劣于 TRGM（模板数据下两者均为 0，不会误失败）
        assertThat(bm25.recallAtK)
                .as("BM25 recall@k 应不劣于 TRGM（达标后方可把 MD_KEYWORD_MODE 默认翻 BM25）")
                .isGreaterThanOrEqualTo(trgm.recallAtK);
    }

    /** 用指定 keyword-mode 跑全部 case，累计 recall@k / 命中率 / MRR。 */
    private Score scoreMode(JdbcTemplate jdbc, List<JsonNode> cases, String mode) {
        MdKeywordSearchServiceImpl service = new MdKeywordSearchServiceImpl(jdbc);
        ReflectionTestUtils.setField(service, "keywordMode", mode);
        ReflectionTestUtils.setField(service, "bm25Tokenizer", "md_tok");
        ReflectionTestUtils.setField(service, "bm25Index", "idx_md_child_bm25");

        double recallSum = 0;
        double mrrSum = 0;
        int casesWithAnyHit = 0;
        for (JsonNode c : cases) {
            UUID spaceId = UUID.fromString(c.get("spaceId").asText());
            int topK = c.has("topK") ? c.get("topK").asInt() : 10;
            Set<String> relevant = new LinkedHashSet<>();
            c.get("relevantChildIds").forEach(n -> relevant.add(n.asText()));

            SearchResponse resp = service.search(spaceId,
                    new SearchRequest(c.get("query").asText(), topK, null, null));
            List<String> ranked = resp.hits().stream().map(SearchHit::chunkId).toList();

            int found = 0;
            int firstRelevantRank = 0;
            for (int i = 0; i < ranked.size(); i++) {
                if (relevant.contains(ranked.get(i))) {
                    found++;
                    if (firstRelevantRank == 0) {
                        firstRelevantRank = i + 1;
                    }
                }
            }
            recallSum += relevant.isEmpty() ? 0 : (double) found / relevant.size();
            if (found > 0) {
                casesWithAnyHit++;
                mrrSum += 1.0 / firstRelevantRank;
            }
        }
        int n = cases.size();
        return new Score(recallSum / n, (double) casesWithAnyHit / n, mrrSum / n);
    }

    private List<JsonNode> loadTestset() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<JsonNode> cases = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(TESTSET)) {
            if (in == null) {
                throw new IllegalStateException("找不到评估集：" + TESTSET);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.strip();
                    // 允许 // 行注释（模板说明用），跳过空行
                    if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                        cases.add(mapper.readTree(trimmed));
                    }
                }
            }
        }
        return cases;
    }

    private JdbcTemplate buildJdbcTemplate() {
        String url = System.getenv("MD_EVAL_DB_URL");
        String user = System.getenv("MD_EVAL_DB_USER");
        String password = System.getenv("MD_EVAL_DB_PASSWORD");
        assertThat(url).as("环境变量 MD_EVAL_DB_URL").isNotBlank();
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        ds.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(ds);
    }

    private String renderScorecard(int total, Score trgm, Score bm25) {
        StringBuilder sb = new StringBuilder();
        sb.append("===== md 关键词 TRGM vs BM25 评估记分卡 =====\n");
        sb.append(String.format("样本总数：%d%n", total));
        sb.append(String.format("%-6s recall@k=%.3f  命中率=%.3f  MRR=%.3f%n",
                "TRGM", trgm.recallAtK, trgm.hitRate, trgm.mrr));
        sb.append(String.format("%-6s recall@k=%.3f  命中率=%.3f  MRR=%.3f%n",
                "BM25", bm25.recallAtK, bm25.hitRate, bm25.mrr));
        sb.append(String.format("门控：BM25 recall@k 不劣于 TRGM  %s%n",
                bm25.recallAtK >= trgm.recallAtK ? "PASS" : "FAIL"));
        return sb.toString();
    }

    private record Score(double recallAtK, double hitRate, double mrr) {}
}
