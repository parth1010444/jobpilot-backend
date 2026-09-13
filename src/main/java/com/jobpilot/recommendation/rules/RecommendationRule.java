package com.jobpilot.recommendation.rules;

import com.jobpilot.recommendation.RecommendationCandidate;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import java.time.Clock;
import java.util.Optional;

/**
 * One deterministic recommendation rule. Rules are evaluated independently;
 * the engine picks the best match by priority then urgency.
 */
public interface RecommendationRule {

    /**
     * @return a match with an urgency score (higher = more urgent within the same priority),
     *         or empty if this rule does not apply
     */
    Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock);

    record RuleMatch(RecommendationResponse recommendation, long urgency) {
    }
}
