--liquibase formatted sql

--changeset kansei:001-create-tailwind-users-table
-- user_id is the shieldwall user id, a plain column and never a FK. A row exists only after the user opts in.
CREATE TABLE tailwind_users (
    user_id UUID PRIMARY KEY,
    joined_at TIMESTAMPTZ NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
);
