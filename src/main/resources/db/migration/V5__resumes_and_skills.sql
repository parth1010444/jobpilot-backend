-- Phase 5: resumes, user skills, and optional application → resume link.
-- Compatible with PostgreSQL and H2 MODE=PostgreSQL.
-- Deleting a resume SET NULL on applications.resume_id (portfolio: keep the application).

CREATE TABLE resumes (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    version_label VARCHAR(50),
    description TEXT,
    file_url VARCHAR(2048),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_resumes PRIMARY KEY (id),
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_resumes_user_updated_at ON resumes (user_id, updated_at);

CREATE TABLE user_skills (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_user_skills PRIMARY KEY (id),
    CONSTRAINT fk_user_skills_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_user_skills_user_name UNIQUE (user_id, name)
);

CREATE INDEX idx_user_skills_user_id ON user_skills (user_id);

ALTER TABLE applications ADD COLUMN resume_id UUID;

ALTER TABLE applications ADD CONSTRAINT fk_applications_resume
    FOREIGN KEY (resume_id) REFERENCES resumes(id) ON DELETE SET NULL;

CREATE INDEX idx_applications_resume_id ON applications (resume_id);
