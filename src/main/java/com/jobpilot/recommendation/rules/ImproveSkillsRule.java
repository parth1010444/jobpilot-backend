package com.jobpilot.recommendation.rules;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.recommendation.RecommendationAction;
import com.jobpilot.recommendation.RecommendationCandidate;
import com.jobpilot.recommendation.RecommendationPriority;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Optional: when a stored job analysis exists and match score &lt; 50 → IMPROVE_SKILLS LOW.
 * Skipped for terminal / offer statuses where skill gaps are less actionable.
 */
@Component
public class ImproveSkillsRule implements RecommendationRule {

    private static final int SCORE_THRESHOLD = 50;
    private static final Set<ApplicationStatus> SKIP_STATUSES = Set.of(
            ApplicationStatus.OFFER,
            ApplicationStatus.REJECTED,
            ApplicationStatus.WITHDRAWN
    );

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (SKIP_STATUSES.contains(candidate.status())) {
            return Optional.empty();
        }
        Integer score = candidate.matchScore();
        if (score == null || score >= SCORE_THRESHOLD) {
            return Optional.empty();
        }
        long urgency = SCORE_THRESHOLD - score;
        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.IMPROVE_SKILLS,
                RecommendationPriority.LOW,
                "Improve skills for this role",
                "Job match score is " + score + "% (below " + SCORE_THRESHOLD + ")",
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, urgency));
    }
}
