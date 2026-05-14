--liquibase formatted sql

--changeset elliot:022-add-complaint-escalation-tables
CREATE TABLE complaints (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID,
    order_id    VARCHAR(100) NOT NULL,
    content     TEXT NOT NULL,
    status      VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_complaints_user_id ON complaints(user_id);
CREATE INDEX idx_complaints_order_id ON complaints(order_id);
CREATE INDEX idx_complaints_status ON complaints(status);

CREATE TABLE complaint_plans (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    complaint_id          UUID NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    responsible_party     VARCHAR(30) NOT NULL,
    responsibility_reason TEXT,
    compensation_type     VARCHAR(40) NOT NULL DEFAULT 'NONE',
    compensation_amount   NUMERIC(12, 2),
    contact_sequence      JSONB NOT NULL DEFAULT '[]'::jsonb,
    timeouts              JSONB NOT NULL DEFAULT '{}'::jsonb,
    fallback_plan         JSONB NOT NULL DEFAULT '[]'::jsonb,
    replan_count          INTEGER NOT NULL DEFAULT 0,
    status                VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_complaint_plans_complaint_id ON complaint_plans(complaint_id);
CREATE INDEX idx_complaint_plans_status ON complaint_plans(status);
