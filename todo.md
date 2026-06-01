# TODO

## Langfuse Tracing 待补功能

### 1. Generation Input / Output 映射

**状态**：未完成

**现象**：

- Langfuse 已能看到 trace、span、generation。
- `chat qwen-plus`、`embedding text-embedding-v2` 等 generation 已上报。
- ClickHouse `observations.input` / `observations.output` 当前为 `NULL`。
- Langfuse UI 中 generation 详情页看不到 Input / Output。

**原因**：

当前通过 Spring AI + OpenTelemetry 自动观测生成 generation span，但 prompt / completion 没有被映射到 Langfuse 可识别的 `input` / `output` 字段。

**待实现**：

- 普通问答：把用户问题写入 input，把最终答案写入 output。
- Agentic 问答：把用户问题、必要上下文摘要、最终答案写入 input / output。
- 工具调用：把 tool 参数和返回结果写入 tool span 的 input / output。
- 检索链路：把 query、topK、命中文档摘要写入 retrieval span 的 input / output 或 metadata。
- 入库链路：Markdown 上传和解析 span 写入 documentId、filename、chunkCount、status、error 等 metadata。

**安全要求**：

- 写入 Langfuse 前必须经过脱敏。
- prompt、completion、tool 参数、retrieval context 必须按配置截断。
- 不影响真实发送给模型的 prompt / completion。

**验收标准**：

- Langfuse UI 中打开 `chat qwen-plus` generation，可以看到 Input 和 Output。
- ClickHouse 查询 `observations.input` / `observations.output` 不再为 `NULL`。
- 密码、Token、API Key、手机号、邮箱等敏感内容不会明文进入 Langfuse。
- 超长正文有截断标记。

验证 SQL：

```bash
docker exec kb-clickhouse clickhouse-client \
  --user clickhouse \
  --password "$(grep '^CLICKHOUSE_PASSWORD=' .env | cut -d= -f2-)" \
  --database default \
  --query "
SELECT name, type, input, output, start_time
FROM observations
WHERE type = 'GENERATION'
ORDER BY start_time DESC
LIMIT 5
FORMAT Vertical"
```

### 2. 过滤非业务链路噪音

**状态**：未完成

**现象**：

- Langfuse 中出现大量 Spring Boot 自动观测数据。
- 典型噪音包括：
  - `task complaint-deadline-scheduler.check-deadlines`
  - `security filterchain before`
  - `security filterchain after`
  - `authorize request`
  - `authorize method`
  - `secured request`
  - `http get /actuator/health`
- `task complaint-deadline-scheduler.check-deadlines` 来自投诉超时检查定时任务，每 60 秒执行一次，因此会持续产生 trace。

**原因**：

当前 tracing 开启后，Spring Boot Micrometer Observation 会自动采集 HTTP、Security、Scheduler 等基础设施 observation，并通过 OTLP 一起上报到 Langfuse。Langfuse 当前被用作 LLM / RAG / 入库链路观测平台，不需要接收所有 Spring 基础设施 span。

**待实现**：

- 增加 tracing 过滤策略，只保留业务相关链路。
- 建议保留：
  - `kb.*`
  - `chat *`
  - `embedding *`
  - `gen_ai.*`
  - `milvus query`
  - `http post /api/v1/spaces/{spaceId}/md-documents/upload`
  - `http post /api/v1/spaces/{spaceId}/md-qa/*`
- 建议过滤：
  - `task complaint-deadline-scheduler.*`
  - `security filterchain *`
  - `authorize request`
  - `authorize method`
  - `secured request`
  - `http get /actuator/health`

**实现方向**：

- 在 tracing 配置中增加 `ObservationPredicate` 或等价过滤逻辑。
- 过滤规则需要可配置，避免后续排查 Spring Security 或 Scheduler 问题时完全不可见。
- 默认面向 Langfuse 使用场景：只保留 LLM / RAG / Markdown 入库相关链路。

**验收标准**：

- Langfuse 中不再持续出现 `task complaint-deadline-scheduler.check-deadlines`。
- 健康检查 `/actuator/health` 不再进入 Langfuse。
- 登录、鉴权产生的 security filterchain / authorize span 不再刷屏。
- Markdown 上传仍能看到 `md-documents/upload`、`kb.ingest.document`、`kb.ingest.parse`。
- 问答仍能看到 `kb.qa.ask`、`kb.retrieval.*`、`chat *`、`embedding *`。
