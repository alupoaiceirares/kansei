--liquibase formatted sql

--changeset kansei:001-create-audit-log-table
-- Append-only permanent audit record, one row per consumed audit.events message. event_id dedupes
-- redelivery (INSERT ... ON CONFLICT (event_id) DO NOTHING in the consumer) - occurred_at is when the
-- source service did the thing, received_at is when fdr actually stored it.
CREATE TABLE audit_log (
                            id BIGSERIAL PRIMARY KEY,
                            event_id UUID NOT NULL UNIQUE,
                            service VARCHAR(50) NOT NULL,
                            action VARCHAR(100) NOT NULL,
                            actor_user_id UUID,
                            actor_username VARCHAR(255),
                            target_type VARCHAR(50),
                            target_id VARCHAR(255),
                            details JSONB NOT NULL DEFAULT '{}',
                            trace_id VARCHAR(64),
                            occurred_at TIMESTAMPTZ NOT NULL,
                            received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

--changeset kansei:002-index-audit-log-occurred-at
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);

--changeset kansei:003-index-audit-log-actor
CREATE INDEX idx_audit_log_actor_occurred_at ON audit_log (actor_user_id, occurred_at);

--changeset kansei:004-index-audit-log-service-action
CREATE INDEX idx_audit_log_service_action_occurred_at ON audit_log (service, action, occurred_at);
