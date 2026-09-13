package com.jobpilot.jobanalysis;

import java.util.List;

/**
 * Result of analyzing a job description.
 * {@code requiredSkills} are canonical, lowercase skill names (sorted, unique).
 */
public record JobAnalysisResult(List<String> requiredSkills) {
}
