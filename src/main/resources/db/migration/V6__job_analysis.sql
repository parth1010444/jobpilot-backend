-- Phase 6: persisted job requirements extracted from application job descriptions.
-- Compatible with PostgreSQL and H2 MODE=PostgreSQL.
-- Re-analyze replaces rows for the application (delete + insert in service).

CREATE TABLE job_requirements (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_job_requirements PRIMARY KEY (id),
    CONSTRAINT fk_job_requirements_application
        FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT uq_job_requirements_application_skill UNIQUE (application_id, skill_name)
);

CREATE INDEX idx_job_requirements_application_id ON job_requirements (application_id);
