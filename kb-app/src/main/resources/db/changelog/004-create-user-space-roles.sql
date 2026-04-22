--liquibase formatted sql
--changeset kb:004-create-user-space-roles

CREATE TABLE IF NOT EXISTS user_space_roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    space_id    UUID REFERENCES spaces(id) ON DELETE CASCADE,
    role_type   VARCHAR(20) NOT NULL,
    granted_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by  UUID REFERENCES users(id),
    UNIQUE (user_id, space_id, role_type)
);

CREATE INDEX IF NOT EXISTS idx_usr_user_id ON user_space_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_usr_space_id ON user_space_roles(space_id);
