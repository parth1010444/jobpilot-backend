package com.jobpilot.reminder.dto;

import com.jobpilot.reminder.ReminderStatus;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateReminderRequest(
        @Size(max = 255) String title,
        String description,
        Instant scheduledAt,
        ReminderStatus status
) {
}
