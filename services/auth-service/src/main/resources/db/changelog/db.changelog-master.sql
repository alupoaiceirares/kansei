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