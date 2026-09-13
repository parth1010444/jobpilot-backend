package com.jobpilot.jobanalysis.dto;

import java.util.List;

/**
 * Explainable match result. {@code score} is coverage of required skills by the user's skills,
 * not a hiring probability. See {@link com.jobpilot.jobanalysis.JobMatchEngine}.
 */
public record JobMatchResponse(
        int score,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> requiredSkills
) {
}
