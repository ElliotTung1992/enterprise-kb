--liquibase formatted sql

--changeset kb:032-drop-agent-traces
-- 下线自研 Agent Trace 体系：删 agent_traces / agent_trace_steps 两表，同时拆 eval_cases.source_trace_id 外键与列。
-- eval_cases 保留，但不再追踪来源 trace（未来如接 LangSmith/LangFuse 等外部 trace 平台，对接方式由独立设计决定）。

-- 1. 拆 eval_cases 对 agent_traces 的外键 + 索引 + 列
ALTER TABLE eval_cases DROP CONSTRAINT IF EXISTS eval_cases_source_trace_id_fkey;
DROP INDEX IF EXISTS idx_eval_cases_source_trace;
ALTER TABLE eval_cases DROP COLUMN IF EXISTS source_trace_id;

-- 2. drop 两个 trace 表（agent_trace_steps 有 FK 引用 agent_traces，CASCADE 会自动级联，但为了语义清晰显式按依赖顺序）
DROP TABLE IF EXISTS agent_trace_steps;
DROP TABLE IF EXISTS agent_traces;
