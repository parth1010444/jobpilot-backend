package com.jobpilot.recommendation.rules;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.recommendation.RecommendationAction;
import com.jobpilot.recommendation.RecommendationCandidate;
import com.jobpilot.recommendation.RecommendationPriority;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * REJECTED → REVIEW_FEEDBACK. MEDIUM if rejected within the last 14 days, otherwise LOW.
 */
@Component
public class ReviewFeedbackRule implements RecommendationRule {

    private static final Duration RECENT = Duration.ofDays(14);

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (candidate.status() != ApplicationStatus.REJECTED) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant updatedAt = candidate.updatedAt() != null ? candidate.updatedAt() : candidate.createdAt();
        if (updatedAt == null) {
            return Optional.empty();
        }
        Duration since = Duration.between(updatedAt, now);
        RecommendationPriority priority = since.compareTo(RECENT) <= 0
                ? RecommendationPriority.MEDIUM
                : RecommendationPriority.LOW;
        long days = Math.max(since.toDays(), 0);

        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.REVIEW_FEEDBACK,
                priority,
                "Review rejection feedback",
                "Application was rejected; capture lessons learned",
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, days));
    }
}
