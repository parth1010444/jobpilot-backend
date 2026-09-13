package com.jobpilot.reminder.dto;

import com.jobpilot.reminder.Reminder;
import com.jobpilot.reminder.ReminderStatus;
import com.jobpilot.reminder.ReminderType;
import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        UUID userId,
        UUID applicationId,
        ReminderType type,
        String title,
        String description,
        Instant scheduledAt,
        ReminderStatus status,
        Instant createdAt,
        Instant completedAt
) {
    public static ReminderResponse from(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getUserId(),
                reminder.getApplicationId(),
                reminder.getType(),
                reminder.getTitle(),
                reminder.getDescription(),
                reminder.getScheduledAt(),
                reminder.getStatus(),
                reminder.getCreatedAt(),
                reminder.getCompletedAt()
        );
    }
}
