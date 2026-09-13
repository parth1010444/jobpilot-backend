package com.jobpilot.analytics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Time series of application activity.
 *
 * <ul>
 *   <li>{@code applicationsCreated} — counted by {@code createdAt}</li>
 *   <li>{@code applicationsApplied} — counted by {@code appliedAt} (apps with
 *       null {@code appliedAt} are omitted from this series)</li>
 * </ul>
 *
 * <p>Buckets: {@code DAY} (calendar day UTC), {@code WEEK} (Monday-start ISO
 * week UTC), {@code MONTH} (first day of calendar month UTC). Default range
 * when {@code from}/{@code to} omitted: last 12 weeks ending today (UTC).
 */
public record AnalyticsTimelineResponse(
        TimelineBucket bucket,
        LocalDate from,
        LocalDate to,
        List<TimelinePoint> points
) {
}
