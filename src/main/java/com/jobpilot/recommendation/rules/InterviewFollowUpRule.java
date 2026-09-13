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
 * INTERVIEW with a recent COMPLETED interview and ≥3 days since completion
 * (application still INTERVIEW) → INTERVIEW_FOLLOW_UP MEDIUM.
 *
 * <p>Completion time is approximated by the interview's {@code updatedAt}
 * (when it was marked COMPLETED).
 */
@Component
public class InterviewFollowUpRule implements RecommendationRule {

    private static final Duration MIN_SINCE_COMPLETION = Duration.ofDays(3);

    @Override
    public Optional<RuleMatch> evaluate(RecommendationCandidate candidate, Clock clock) {
        if (candidate.status() != ApplicationStatus.INTERVIEW) {
            return Optional.empty();
        }
        Instant now = clock.instant();

        Optional<InterviewInfo> latestCompleted = candidate.interviews().stream()
                .filter(i -> i.status() == InterviewStatus.COMPLETED)
                .filter(i -> i.updatedAt() != null)
                .max(Comparator.comparing(InterviewInfo::updatedAt));

        if (latestCompleted.isEmpty()) {
            return Optional.empty();
        }

        // If there is still an upcoming SCHEDULED interview, prefer prepare over follow-up
        boolean hasUpcomingScheduled = candidate.interviews().stream()
                .anyMatch(i -> i.status() == InterviewStatus.SCHEDULED
                        && i.scheduledAt() != null
                        && !i.scheduledAt().isBefore(now));
        if (hasUpcomingScheduled) {
            return Optional.empty();
        }

        Instant completedAt = latestCompleted.get().updatedAt();
        Duration since = Duration.between(completedAt, now);
        if (since.compareTo(MIN_SINCE_COMPLETION) < 0) {
            return Optional.empty();
        }

        long days = since.toDays();
        RecommendationResponse response = new RecommendationResponse(
                RecommendationAction.INTERVIEW_FOLLOW_UP,
                RecommendationPriority.MEDIUM,
                "Follow up after interview",
                "Interview completed " + days + " day" + (days == 1 ? "" : "s")
                        + " ago with no status change",
                candidate.applicationId()
        );
        return Optional.of(new RuleMatch(response, days));
    }
}
