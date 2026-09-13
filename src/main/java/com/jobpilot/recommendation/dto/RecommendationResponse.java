package com.jobpilot.recommendation.dto;

import com.jobpilot.recommendation.RecommendationAction;
import com.jobpilot.recommendation.RecommendationPriority;
import java.util.UUID;

/**
 * Single next-action recommendation for an application (compute-on-read).
 */
public record RecommendationResponse(
        RecommendationAction action,
        RecommendationPriority priority,
        String title,
        String reason,
        UUID applicationId
) {
}
