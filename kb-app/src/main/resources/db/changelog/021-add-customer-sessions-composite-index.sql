--liquibase formatted sql

--changeset elliot:021-add-customer-sessions-composite-index
CREATE INDEX idx_customer_sessions_user_deleted ON customer_sessions(user_id, deleted_at);
