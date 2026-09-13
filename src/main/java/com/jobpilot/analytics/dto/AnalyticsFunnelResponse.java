package com.jobpilot.analytics.dto;

import java.util.List;

/**
 * Ordered funnel stages {@code SAVED → APPLIED → OA → INTERVIEW → OFFER}
 * plus side stats for terminal statuses that sit outside the main path.
 */
public record AnalyticsFunnelResponse(
        List<FunnelStage> stages,
        long rejectedCount,
        long withdrawnCount
) {
}
