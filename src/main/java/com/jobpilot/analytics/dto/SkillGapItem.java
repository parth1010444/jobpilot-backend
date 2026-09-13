package com.jobpilot.analytics.dto;

public record SkillGapItem(
        String skillName,
        long missingCount
) {
}
