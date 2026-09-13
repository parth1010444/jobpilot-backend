package com.jobpilot.jobanalysis;

/**
 * Extracts required skills from a free-text job description.
 * Phase 6 ships a deterministic rule-based implementation (no external LLM).
 */
public interface JobDescriptionAnalyzer {

    JobAnalysisResult analyze(String jobDescription);
}
