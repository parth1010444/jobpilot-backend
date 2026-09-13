package com.jobpilot.analytics.dto;

import java.util.List;

/**
 * Aggregate snapshot for the authenticated user.
 *
 * <p>{@code activeApplications} = applications whose status is not
 * {@code REJECTED} or {@code WITHDRAWN}.
 *
 * <p>{@code averageMatchScore} is the arithmetic mean of live
 * {@link com.jobpilot.jobanalysis.JobMatchEngine} scores for applications that
 * have stored {@code job_requirements}. Null when no analyzed applications
 * exist. Match scores are not persisted; this recomputes against the user's
 * current skills.
 */
public record AnalyticsSummaryResponse(
        long totalApplications,
        List<StatusCount> countsByStatus,
        long interviewCount,
        long offerCount,
        long rejectedCount,
        Double averageMatchScore,
        long activeApplications
) {
}
