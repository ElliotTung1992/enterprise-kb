--liquibase formatted sql

--changeset elliot:023-add-complaint-plan-next-check-at
ALTER TABLE complaint_plans ADD COLUMN next_check_at TIMESTAMPTZ;

COMMENT ON COLUMN complaint_plans.next_check_at IS '下次 deadline 检查时间，EXECUTING 状态时由 Executor 写入（now + 48h）';
