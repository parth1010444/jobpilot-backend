-- Phase 1 placeholder schema. Domain tables are introduced in later phases.
-- Hibernate ddl-auto is none; Flyway is the source of schema changes.

CREATE TABLE users (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
);
