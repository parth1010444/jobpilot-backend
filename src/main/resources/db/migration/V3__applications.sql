-- Phase 3: job applications + status history (H2 MODE=PostgreSQL + PostgreSQL compatible).

CREATE TABLE applications (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    company VARCHAR(255) NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    job_url VARCHAR(2048),
    location VARCHAR(255),
    employment_type VARCHAR(50),
    source VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    salary_min INTEGER,
    salary_max INTEGER,
    job_description TEXT,
    notes TEXT,
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_applications PRIMARY KEY (id),
    CONSTRAINT fk_applications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_applications_user_status ON applications (user_id, status);
CREATE INDEX idx_applications_user_applied_at ON applications (user_id, applied_at);
CREATE INDEX idx_applications_user_company ON applications (user_id, company);

CREATE TABLE application_status_history (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_application_status_history PRIMARY KEY (id),
    CONSTRAINT fk_app_status_history_application FOREIGN KEY (application_id) REFERENCES applications(id)
);

CREATE INDEX idx_app_status_history_application ON application_status_history (application_id);
