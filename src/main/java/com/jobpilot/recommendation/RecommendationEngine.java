package com.jobpilot.recommendation;

import com.jobpilot.recommendation.dto.RecommendationResponse;
import com.jobpilot.recommendation.rules.RecommendationRule;
import com.jobpilot.recommendation.rules.RecommendationRule.RuleMatch;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rule-based recommendation engine (no ML/LLM). Evaluates all registered
 * {@link RecommendationRule}s against a candidate and selects the best match
 * by priority (HIGH before LOW) then urgency (higher first).
 *
 * <p>Clock is injectable so unit tests can freeze time.
 */
@Component
public class RecommendationEngine {

    /**
     * Best match first: lower priority sortOrder, then higher urgency.
     */
    public static final Comparator<RuleMatch> BEST_FIRST = Comparator
            .comparingInt((RuleMatch m) -> m.recommendation().priority().sortOrder())
            .thenComparing(Comparator.comparingLong(RuleMatch::urgency).reversed());

    private final List<RecommendationRule> rules;
    private final Clock clock;

    public RecommendationEngine(List<RecommendationRule> rules, Clock clock) {
        this.rules = List.copyOf(rules);
        this.clock = clock;
    }

    /**
     * Best single next action for one application, if any rule fires.
     */
    public Optional<RecommendationResponse> recommend(RecommendationCandidate candidate) {
        return evaluateAll(candidate).stream()
                .min(BEST_FIRST)
                .map(RuleMatch::recommendation);
    }

    /**
     * Best match with urgency retained (for cross-application sorting).
     */
    public Optional<RuleMatch> recommendScored(RecommendationCandidate candidate) {
        return evaluateAll(candidate).stream().min(BEST_FIRST);
    }

    /**
     * All firing rules for diagnostics / tests (unsorted).
     */
    public List<RuleMatch> evaluateAll(RecommendationCandidate candidate) {
        List<RuleMatch> matches = new ArrayList<>();
        for (RecommendationRule rule : rules) {
            rule.evaluate(candidate, clock).ifPresent(matches::add);
        }
        return matches;
    }
}
