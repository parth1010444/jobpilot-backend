package com.jobpilot.recommendation.rules;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.interview.InterviewStatus;
import com.jobpilot.recommendation.RecommendationAction;
import com.jobpilot.recommendation.RecommendationCandidate;
import com.jobpilot.recommendation.RecommendationCandidate.InterviewInfo;
import com.jobpilot.recommendation.RecommendationPriority;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * INTERVIEW with a SCHEDULED interview within the next 2 days → PREPARE_FOR_INTERVIEW HIGH.
 */
@Component
public class PrepareForInterviewRule implements RecommendationRule {

    private static final Duration WINDOW = Duration.ofDays(2);

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (candidate.status() != ApplicationStatus.INTERVIEW) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant windowEnd = now.plus(WINDOW);

        Optional<InterviewInfo> upcoming = candidate.interviews().stream()
                .filter(i -> i.status() == InterviewStatus.SCHEDULED)
                .filter(i -> i.scheduledAt() != null)
                .filter(i -> !i.scheduledAt().isBefore(now))
                .filter(i -> !i.scheduledAt().isAfter(windowEnd))
                .min(Comparator.comparing(InterviewInfo::scheduledAt));

        if (upcoming.isEmpty()) {
            return Optional.empty();
        }

        Instant scheduledAt = upcoming.get().scheduledAt();
        long hoursUntil = Duration.between(now, scheduledAt).toHours();
        // Sooner interview → higher urgency
        long urgency = WINDOW.toHours() - hoursUntil;

        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.PREPARE_FOR_INTERVIEW,
                RecommendationPriority.HIGH,
                "Prepare for upcoming interview",
                "Interview scheduled within 2 days",
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, urgency));
    }
}
