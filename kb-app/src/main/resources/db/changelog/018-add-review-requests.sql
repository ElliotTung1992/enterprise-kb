--liquibase formatted sql

--changeset elliot:018-add-review-requests
CREATE TABLE review_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID        NOT NULL,
    space_id        UUID        NOT NULL,
    user_id         UUID,
    order_id        VARCHAR(100),
    reason          TEXT,
    conversation_snapshot JSONB,
    order_details   JSONB,
    -- HITL 恢复所需：存储被拦截的 LLM 工具调用 ID，resume 时重建 InterruptionMetadata
    tool_call_id    VARCHAR(200),
    tool_call_name  VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_id     UUID,
    reviewer_comment TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_at      TIMESTAMPTZ
);

CREATE INDEX idx_review_requests_status     ON review_requests(status);
CREATE INDEX idx_review_requests_session_id ON review_requests(session_id);
CREATE INDEX idx_review_requests_space_id   ON review_requests(space_id);
