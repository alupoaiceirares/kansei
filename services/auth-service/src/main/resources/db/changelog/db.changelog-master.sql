--liquibase formatted sql

--changeset kansei:001-create-users-table
CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       username VARCHAR(255) NOT NULL UNIQUE,
                       first_name VARCHAR(255),
                       last_name VARCHAR(255),
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMP NOT NULL,
                       updated_at TIMESTAMP NOT NULL
);

--changeset kansei:002-add-credentials-version-to-users
-- Bumped whenever password or email changes. Every JWT carries the version it was issued under as a claim
-- a mismatch against the current DB value means the token predates the change and gets rejected, even if unexpired.
ALTER TABLE users ADD COLUMN credentials_version INTEGER NOT NULL DEFAULT 0;

--changeset kansei:003-add-deactivated-at-to-users
-- Set when a user deactivates their account. NULL means the account is not pending deletion. Login within the retention window clears it and reactivates; the purge job hard-deletes rows past the window.
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP;