--liquibase formatted sql
--changeset kb:016-create-qa-sessions

CREATE TABLE IF NOT EXISTS qa_sessions (
    id           UUID PRIMARY KEY,
    space_id     UUID NOT NULL,
    user_id      UUID NOT NULL,
    title        VARCHAR(200) NOT NULL DEFAULT '新对话',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_qa_sessions_space_user ON qa_sessions(space_id, user_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS qa_messages (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id   UUID NOT NULL REFERENCES qa_sessions(id) ON DELETE CASCADE,
    role         VARCHAR(20) NOT NULL,
    content      TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_qa_messages_session_id ON qa_messages(session_id);
