package com.jobpilot.interview.dto;

import com.jobpilot.interview.Interview;
import com.jobpilot.interview.InterviewStatus;
import com.jobpilot.interview.InterviewType;
import java.time.Instant;
import java.util.UUID;

public record InterviewResponse(
        UUID id,
        UUID applicationId,
        int roundNumber,
        InterviewType type,
        InterviewStatus status,
        Instant scheduledAt,
        String interviewer,
        String meetingLink,
        String notes,
        String feedback,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static InterviewResponse from(Interview interview) {
        return new InterviewResponse(
                interview.getId(),
                interview.getApplication().getId(),
                interview.getRoundNumber(),
                interview.getType(),
                interview.getStatus(),
                interview.getScheduledAt(),
                interview.getInterviewer(),
                interview.getMeetingLink(),
                interview.getNotes(),
                interview.getFeedback(),
                interview.getCreatedAt(),
                interview.getUpdatedAt(),
                interview.getVersion()
        );
    }
}
