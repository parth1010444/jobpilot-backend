package com.jobpilot.analytics.dto;

import com.jobpilot.application.ApplicationStatus;

/**
 * One stage of the job funnel. {@code count} is the number of applications
 * currently in that status (snapshot, not cumulative).
 *
 * <p>{@code conversionFromPrevious} = {@code count / previousStage.count}
 * when the previous stage count is &gt; 0; otherwise null. First stage is
 * always null.
 */
public record FunnelStage(
        ApplicationStatus status,
        long count,
        Double conversionFromPrevious
) {
}
