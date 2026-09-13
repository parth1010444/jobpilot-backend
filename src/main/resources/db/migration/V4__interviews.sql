-- Phase 4: interview rounds belonging to user-owned applications.

CREATE TABLE interviews (
    id UUID NOT NULL,
    application_id UUID NOT NULL,
    round_number INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    interviewer VARCHAR(255),
    meeting_link VARCHAR(2048),
    notes TEXT,
    feedback TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_interviews PRIMARY KEY (id),
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE CASCADE,
    CONSTRAINT uq_interviews_application_round UNIQUE (application_id, round_number),
    CONSTRAINT chk_interviews_round_positive CHECK (round_number > 0),
    CONSTRAINT chk_interviews_type CHECK (type IN ('OA', 'TECHNICAL', 'SYSTEM_DESIGN', 'MANAGERIAL', 'HR', 'OTHER')),
    CONSTRAINT chk_interviews_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW'))
);

CREATE INDEX idx_interviews_application_scheduled_at ON interviews (application_id, scheduled_at);
CREATE INDEX idx_interviews_application_status ON interviews (application_id, status);
CREATE INDEX idx_interviews_status_scheduled_at ON interviews (status, scheduled_at);
