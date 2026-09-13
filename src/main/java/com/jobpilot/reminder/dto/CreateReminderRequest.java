package com.jobpilot.reminder.dto;

import com.jobpilot.reminder.ReminderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateReminderRequest(
        UUID applicationId,
        @NotNull ReminderType type,
        @NotBlank @Size(max = 255) String title,
        String description,
        @NotNull Instant scheduledAt
) {
}
