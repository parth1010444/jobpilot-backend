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
 * SAVED and stale (≥14 days since last update) → APPLY_OR_ARCHIVE MEDIUM.
 */
@Component
public class ApplyOrArchiveRule implements RecommendationRule {

    private static final Duration STALE_AFTER = Duration.ofDays(14);

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (candidate.status() != ApplicationStatus.SAVED) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant reference = candidate.updatedAt() != null ? candidate.updatedAt() : candidate.createdAt();
        if (reference == null) {
            return Optional.empty();
        }
        Duration since = Duration.between(reference, now);
        if (since.compareTo(STALE_AFTER) < 0) {
            return Optional.empty();
        }
        long days = since.toDays();
        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.APPLY_OR_ARCHIVE,
                RecommendationPriority.MEDIUM,
                "Apply or archive this saved role",
                "Saved application has been idle for " + days + " days",
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, days));
    }
}
