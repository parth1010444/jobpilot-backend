package com.jobpilot.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.interview.InterviewStatus;
import com.jobpilot.recommendation.RecommendationCandidate.InterviewInfo;
import com.jobpilot.recommendation.dto.RecommendationResponse;
import com.jobpilot.recommendation.rules.ApplyOrArchiveRule;
import com.jobpilot.recommendation.rules.EvaluateOfferRule;
import com.jobpilot.recommendation.rules.FollowUpRule;
import com.jobpilot.recommendation.rules.ImproveSkillsRule;
import com.jobpilot.recommendation.rules.InterviewFollowUpRule;
import com.jobpilot.recommendation.rules.PrepareForInterviewRule;
import com.jobpilot.recommendation.rules.ReviewFeedbackRule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationEngineTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private RecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new RecommendationEngine(
                List.of(
                        new FollowUpRule(),
                        new PrepareForInterviewRule(),
                        new InterviewFollowUpRule(),
                        new ReviewFeedbackRule(),
                        new ApplyOrArchiveRule(),
                        new EvaluateOfferRule(),
                        new ImproveSkillsRule()
                ),
                CLOCK
        );
    }

    @Test
    void appliedStaleSevenDaysYieldsFollowUpHigh() {
        UUID id = UUID.randomUUID();
        RecommendationCandidate candidate = candidate(
                id,
                ApplicationStatus.APPLIED,
                NOW.minusSeconds(8L * 24 * 3600),
                null,
                List.of(),
                null
        );

        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.FOLLOW_UP, response.action());
        assertEquals(RecommendationPriority.HIGH, response.priority());
        assertEquals("Follow up with recruiter", response.title());
        assertEquals("No application update for 8 days", response.reason());
        assertEquals(id, response.applicationId());
    }

    @Test
    void oaStaleAlsoYieldsFollowUp() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.OA,
                NOW.minusSeconds(10L * 24 * 3600),
                null,
                List.of(),
                null
        );
        assertEquals(RecommendationAction.FOLLOW_UP, engine.recommend(candidate).orElseThrow().action());
    }

    @Test
    void appliedRecentDoesNotFollowUp() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.APPLIED,
                NOW.minusSeconds(3L * 24 * 3600),
                null,
                List.of(),
                null
        );
        assertTrue(engine.recommend(candidate).isEmpty());
    }

    @Test
    void interviewScheduledWithinTwoDaysYieldsPrepareHigh() {
        UUID id = UUID.randomUUID();
        RecommendationCandidate candidate = candidate(
                id,
                ApplicationStatus.INTERVIEW,
                NOW.minusSeconds(3600),
                null,
                List.of(new InterviewInfo(
                        InterviewStatus.SCHEDULED,
                        NOW.plusSeconds(36L * 3600),
                        NOW.minusSeconds(3600)
                )),
                null
        );

        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.PREPARE_FOR_INTERVIEW, response.action());
        assertEquals(RecommendationPriority.HIGH, response.priority());
        assertEquals("Prepare for upcoming interview", response.title());
    }

    @Test
    void interviewScheduledBeyondTwoDaysDoesNotPrepare() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.INTERVIEW,
                NOW,
                null,
                List.of(new InterviewInfo(
                        InterviewStatus.SCHEDULED,
                        NOW.plusSeconds(5L * 24 * 3600),
                        NOW
                )),
                null
        );
        assertTrue(engine.recommend(candidate).isEmpty());
    }

    @Test
    void completedInterviewThreeDaysAgoYieldsInterviewFollowUpMedium() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.INTERVIEW,
                NOW.minusSeconds(10L * 24 * 3600),
                null,
                List.of(new InterviewInfo(
                        InterviewStatus.COMPLETED,
                        NOW.minusSeconds(5L * 24 * 3600),
                        NOW.minusSeconds(4L * 24 * 3600)
                )),
                null
        );

        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.INTERVIEW_FOLLOW_UP, response.action());
        assertEquals(RecommendationPriority.MEDIUM, response.priority());
    }

    @Test
    void completedInterviewButUpcomingScheduledPrefersPrepare() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.INTERVIEW,
                NOW,
                null,
                List.of(
                        new InterviewInfo(
                                InterviewStatus.COMPLETED,
                                NOW.minusSeconds(10L * 24 * 3600),
                                NOW.minusSeconds(9L * 24 * 3600)
                        ),
                        new InterviewInfo(
                                InterviewStatus.SCHEDULED,
                                NOW.plusSeconds(24L * 3600),
                                NOW
                        )
                ),
                null
        );

        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.PREPARE_FOR_INTERVIEW, response.action());
    }

    @Test
    void rejectedRecentIsReviewFeedbackMedium() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.REJECTED,
                NOW.minusSeconds(3L * 24 * 3600),
                null,
                List.of(),
                null
        );
        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.REVIEW_FEEDBACK, response.action());
        assertEquals(RecommendationPriority.MEDIUM, response.priority());
    }

    @Test
    void rejectedOldIsReviewFeedbackLow() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.REJECTED,
                NOW.minusSeconds(30L * 24 * 3600),
                null,
                List.of(),
                null
        );
        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.REVIEW_FEEDBACK, response.action());
        assertEquals(RecommendationPriority.LOW, response.priority());
    }

    @Test
    void savedStaleFourteenDaysYieldsApplyOrArchiveMedium() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.SAVED,
                NOW.minusSeconds(20L * 24 * 3600),
                NOW.minusSeconds(20L * 24 * 3600),
                List.of(),
                null
        );
        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.APPLY_OR_ARCHIVE, response.action());
        assertEquals(RecommendationPriority.MEDIUM, response.priority());
    }

    @Test
    void offerYieldsEvaluateOfferHigh() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.OFFER,
                NOW.minusSeconds(2L * 24 * 3600),
                null,
                List.of(),
                null
        );
        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.EVALUATE_OFFER, response.action());
        assertEquals(RecommendationPriority.HIGH, response.priority());
    }

    @Test
    void lowMatchScoreYieldsImproveSkillsLow() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.APPLIED,
                NOW.minusSeconds(3600),
                null,
                List.of(),
                40
        );
        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.IMPROVE_SKILLS, response.action());
        assertEquals(RecommendationPriority.LOW, response.priority());
        assertTrue(response.reason().contains("40%"));
    }

    @Test
    void highMatchScoreDoesNotImproveSkills() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.APPLIED,
                NOW.minusSeconds(3600),
                null,
                List.of(),
                80
        );
        assertTrue(engine.recommend(candidate).isEmpty());
    }

    @Test
    void highPriorityBeatsLowWhenBothFire() {
        // Stale APPLIED (≥7d) + low match → FOLLOW_UP HIGH wins over IMPROVE_SKILLS LOW
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.APPLIED,
                NOW.minusSeconds(10L * 24 * 3600),
                null,
                List.of(),
                20
        );
        RecommendationResponse response = engine.recommend(candidate).orElseThrow();
        assertEquals(RecommendationAction.FOLLOW_UP, response.action());
        assertEquals(RecommendationPriority.HIGH, response.priority());
    }

    @Test
    void withdrawnHasNoRecommendation() {
        RecommendationCandidate candidate = candidate(
                UUID.randomUUID(),
                ApplicationStatus.WITHDRAWN,
                NOW.minusSeconds(30L * 24 * 3600),
                null,
                List.of(),
                10
        );
        Optional<RecommendationResponse> response = engine.recommend(candidate);
        assertTrue(response.isEmpty());
    }

    private static RecommendationCandidate candidate(
            UUID id,
            ApplicationStatus status,
            Instant updatedAt,
            Instant createdAt,
            List<InterviewInfo> interviews,
            Integer matchScore
    ) {
        return new RecommendationCandidate(
                id,
                status,
                updatedAt,
                createdAt != null ? createdAt : updatedAt,
                interviews,
                matchScore
        );
    }
}
