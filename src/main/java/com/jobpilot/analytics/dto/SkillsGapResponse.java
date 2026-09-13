package com.jobpilot.analytics.dto;

import java.util.List;

/**
 * Top skills required by the user's analyzed applications that the user does
 * not currently have. {@code missingCount} is the number of analyzed
 * applications where that skill is required but absent from the user profile.
 */
public record SkillsGapResponse(
        List<SkillGapItem> missingSkills
) {
}
