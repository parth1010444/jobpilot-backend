package com.jobpilot.recommendation;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.interview.InterviewStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot of application + interview + optional match data fed into {@link RecommendationEngine}.
 * Keeps the engine free of JPA and easy to unit-test with a fixed clock.
 */
public record RecommendationCandidate(
        UUID applicationId,
        ApplicationStatus status,
        Instant updatedAt,
        Instant createdAt,
        List<InterviewInfo> interviews,
        Integer matchScore
) {

    public RecommendationCandidate {
        interviews = interviews == null ? List.of() : List.copyOf(interviews);
    }

    public record InterviewInfo(
            InterviewStatus status,
            Instant scheduledAt,
            Instant updatedAt
    ) {
    }
}
