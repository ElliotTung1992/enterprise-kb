--liquibase formatted sql

--changeset kb:025-add-agent-traces-eval
CREATE TABLE IF NOT EXISTS agent_traces (
    id                UUID PRIMARY KEY,
    trace_type        VARCHAR(50) NOT NULL,
    session_id        UUID,
    space_id          UUID,
    user_id           UUID,
    request_id        VARCHAR(100),
    model_provider    VARCHAR(50),
    status            VARCHAR(30) NOT NULL,
    input_text        TEXT,
    output_text       TEXT,
    raw_input_json    JSONB,
    raw_output_json   JSONB,
    duration_ms       BIGINT,
    tokens_used       INTEGER,
    error_type        VARCHAR(200),
    error_message     TEXT,
    payload_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    started_at        TIMESTAMPTZ NOT NULL,
    completed_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_traces_session_id ON agent_traces(session_id);
CREATE INDEX IF NOT EXISTS idx_agent_traces_space_id ON agent_traces(space_id);
CREATE INDEX IF NOT EXISTS idx_agent_traces_user_id ON agent_traces(user_id);
CREATE INDEX IF NOT EXISTS idx_agent_traces_type_status ON agent_traces(trace_type, status);
CREATE INDEX IF NOT EXISTS idx_agent_traces_started_at ON agent_traces(started_at DESC);

COMMENT ON TABLE agent_traces IS 'Agent/RAG/客服助手单轮请求 trace 外壳，用于复现、评估和后台排障';
COMMENT ON COLUMN agent_traces.trace_type IS '链路类型，如 AGENTIC_QA / STANDARD_QA / CUSTOMER_ASSISTANT / AFTER_SALES / COMPLAINT';
COMMENT ON COLUMN agent_traces.status IS 'RUNNING / SUCCEEDED / FAILED / INTERRUPTED';
COMMENT ON COLUMN agent_traces.raw_input_json IS '完整输入快照（FULL_RAW 模式下包含 prompt/history/user/tool schema 等）';
COMMENT ON COLUMN agent_traces.raw_output_json IS '完整输出快照（FULL_RAW 模式下包含 assistant message/citations 等）';
COMMENT ON COLUMN agent_traces.payload_truncated IS 'raw payload 是否因大小上限被截断';

CREATE TABLE IF NOT EXISTS agent_trace_steps (
    id                UUID PRIMARY KEY,
    trace_id          UUID NOT NULL REFERENCES agent_traces(id) ON DELETE CASCADE,
    step_index        INTEGER NOT NULL,
    step_type         VARCHAR(50) NOT NULL,
    agent_name        VARCHAR(100),
    tool_call_id      VARCHAR(200),
    tool_name         VARCHAR(100),
    status            VARCHAR(30) NOT NULL,
    input_json        JSONB,
    output_json       JSONB,
    raw_payload_json  JSONB,
    business_ref_type VARCHAR(50),
    business_ref_id   UUID,
    duration_ms       BIGINT,
    error_type        VARCHAR(200),
    error_message     TEXT,
    payload_truncated BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_agent_trace_steps_trace_index UNIQUE (trace_id, step_index)
);

CREATE INDEX IF NOT EXISTS idx_agent_trace_steps_trace_id ON agent_trace_steps(trace_id, step_index);
CREATE INDEX IF NOT EXISTS idx_agent_trace_steps_type ON agent_trace_steps(step_type);
CREATE INDEX IF NOT EXISTS idx_agent_trace_steps_tool_name ON agent_trace_steps(tool_name);
CREATE INDEX IF NOT EXISTS idx_agent_trace_steps_business_ref ON agent_trace_steps(business_ref_type, business_ref_id);

COMMENT ON TABLE agent_trace_steps IS 'Agent trace 内的模型调用、工具调用、路由、检索、HITL 中断等步骤';
COMMENT ON COLUMN agent_trace_steps.step_type IS 'MODEL_CALL / TOOL_CALL / ROUTER / GUARD / HITL_INTERRUPT / RESUME / RETRIEVAL / RERANK';
COMMENT ON COLUMN agent_trace_steps.business_ref_type IS '关联业务对象类型，如 DOCUMENT / REVIEW_REQUEST / COMPLAINT / COMPLAINT_PLAN';

CREATE TABLE IF NOT EXISTS eval_cases (
    id                 UUID PRIMARY KEY,
    source_trace_id    UUID REFERENCES agent_traces(id) ON DELETE SET NULL,
    case_type          VARCHAR(50) NOT NULL,
    name               VARCHAR(200) NOT NULL,
    dataset            VARCHAR(100) NOT NULL,
    input_json         JSONB NOT NULL,
    expected_json      JSONB NOT NULL,
    mock_outputs_json  JSONB,
    enabled            BOOLEAN NOT NULL DEFAULT TRUE,
    created_by         UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_eval_cases_dataset ON eval_cases(dataset);
CREATE INDEX IF NOT EXISTS idx_eval_cases_type_enabled ON eval_cases(case_type, enabled);
CREATE INDEX IF NOT EXISTS idx_eval_cases_source_trace ON eval_cases(source_trace_id);

COMMENT ON TABLE eval_cases IS '从 trace 或人工标注沉淀的离线评估用例';
COMMENT ON COLUMN eval_cases.case_type IS 'ROUTING_TOOL / RETRIEVAL / ANSWER / END_TO_END';
COMMENT ON COLUMN eval_cases.mock_outputs_json IS '副作用工具的 mock 输出，用于离线回放避免真实提交';

CREATE TABLE IF NOT EXISTS eval_runs (
    id            UUID PRIMARY KEY,
    dataset       VARCHAR(100) NOT NULL,
    status        VARCHAR(30) NOT NULL,
    config_json   JSONB,
    summary_json  JSONB,
    started_at    TIMESTAMPTZ NOT NULL,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_eval_runs_dataset ON eval_runs(dataset);
CREATE INDEX IF NOT EXISTS idx_eval_runs_status ON eval_runs(status);
CREATE INDEX IF NOT EXISTS idx_eval_runs_started_at ON eval_runs(started_at DESC);

COMMENT ON TABLE eval_runs IS '一次离线评估运行记录，保存数据集、配置、状态和汇总指标';

CREATE TABLE IF NOT EXISTS eval_run_results (
    id                    UUID PRIMARY KEY,
    eval_run_id           UUID NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    eval_case_id          UUID NOT NULL REFERENCES eval_cases(id) ON DELETE CASCADE,
    status                VARCHAR(30) NOT NULL,
    actual_json           JSONB,
    assertion_result_json JSONB,
    failure_reason        TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_eval_run_results_run_case UNIQUE (eval_run_id, eval_case_id)
);

CREATE INDEX IF NOT EXISTS idx_eval_run_results_run_id ON eval_run_results(eval_run_id);
CREATE INDEX IF NOT EXISTS idx_eval_run_results_case_id ON eval_run_results(eval_case_id);
CREATE INDEX IF NOT EXISTS idx_eval_run_results_status ON eval_run_results(status);

COMMENT ON TABLE eval_run_results IS '离线评估中每个 eval case 的执行结果和断言失败原因';
