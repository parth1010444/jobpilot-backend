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
 * OFFER → EVALUATE_OFFER HIGH.
 */
@Component
public class EvaluateOfferRule implements RecommendationRule {

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (candidate.status() != ApplicationStatus.OFFER) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Instant updatedAt = candidate.updatedAt() != null ? candidate.updatedAt() : candidate.createdAt();
        long days = updatedAt == null ? 0 : Math.max(Duration.between(updatedAt, now).toDays(), 0);

        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.EVALUATE_OFFER,
                RecommendationPriority.HIGH,
                "Evaluate offer",
                "You have an open offer — compare compensation, role, and timeline",
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, days));
    }
}
