package com.jobpilot.recommendation;

/**
 * Recommended next action for an application. Computed on read (Phase 7); not persisted.
 */
public enum RecommendationAction {
    FOLLOW_UP,
    PREPARE_FOR_INTERVIEW,
    INTERVIEW_FOLLOW_UP,
    REVIEW_FEEDBACK,
    APPLY_OR_ARCHIVE,
    EVALUATE_OFFER,
    IMPROVE_SKILLS
}
