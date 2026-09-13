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
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * APPLIED or OA with no update for ≥7 days → FOLLOW_UP HIGH.
 */
@Component
public class FollowUpRule implements RecommendationRule {

    private static final Set<ApplicationStatus> STATUSES = Set.of(
            ApplicationStatus.APPLIED,
            ApplicationStatus.OA
    );
    private static final Duration STALE_AFTER = Duration.ofDays(7);

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (!STATUSES.contains(candidate.status())) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant updatedAt = candidate.updatedAt();
        if (updatedAt == null) {
            return Optional.empty();
        }
        Duration sinceUpdate = Duration.between(updatedAt, now);
        if (sinceUpdate.compareTo(STALE_AFTER) < 0) {
            return Optional.empty();
        }
        long days = sinceUpdate.toDays();
        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.FOLLOW_UP,
                RecommendationPriority.HIGH,
                "Follow up with recruiter",
                "No application update for " + days + " day" + (days == 1 ? "" : "s"),
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, days));
    }
}
