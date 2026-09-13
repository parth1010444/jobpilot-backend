-- Phase 8: reminders + in-app notifications (PostgreSQL + H2 MODE=PostgreSQL).

CREATE TABLE reminders (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    application_id UUID,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT pk_reminders PRIMARY KEY (id),
    CONSTRAINT fk_reminders_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_reminders_application FOREIGN KEY (application_id) REFERENCES applications(id) ON DELETE SET NULL,
    CONSTRAINT chk_reminders_type CHECK (type IN (
        'FOLLOW_UP', 'INTERVIEW_PREPARATION', 'INTERVIEW_FOLLOW_UP', 'OFFER_EXPIRY', 'CUSTOM'
    )),
    CONSTRAINT chk_reminders_status CHECK (status IN ('PENDING', 'PROCESSED', 'CANCELLED'))
);

CREATE INDEX idx_reminders_user_id ON reminders (user_id);
CREATE INDEX idx_reminders_status ON reminders (status);
CREATE INDEX idx_reminders_scheduled_at ON reminders (scheduled_at);
CREATE INDEX idx_reminders_status_scheduled_at ON reminders (status, scheduled_at);
CREATE INDEX idx_reminders_user_status ON reminders (user_id, status);

CREATE TABLE notifications (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    reminder_id UUID,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    retry_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_notifications_reminder FOREIGN KEY (reminder_id) REFERENCES reminders(id) ON DELETE SET NULL,
    CONSTRAINT uq_notifications_reminder UNIQUE (reminder_id),
    CONSTRAINT chk_notifications_type CHECK (type IN ('REMINDER', 'SYSTEM')),
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'READ'))
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_user_status ON notifications (user_id, status);
CREATE INDEX idx_notifications_user_created_at ON notifications (user_id, created_at);
