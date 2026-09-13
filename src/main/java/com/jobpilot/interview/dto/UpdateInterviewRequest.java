package com.jobpilot.interview.dto;

import com.jobpilot.interview.InterviewStatus;
import com.jobpilot.interview.InterviewType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record UpdateInterviewRequest(
        @Positive Integer roundNumber,
        InterviewType type,
        InterviewStatus status,
        Instant scheduledAt,
        @Size(max = 255) String interviewer,
        @Size(max = 2048) String meetingLink,
        String notes,
        String feedback,
        Long version
) {
}
