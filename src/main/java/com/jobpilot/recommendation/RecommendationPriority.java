package com.jobpilot.recommendation;

/**
 * Priority for sorting recommendations. Lower {@link #sortOrder()} sorts first (HIGH before LOW).
 */
public enum RecommendationPriority {
    HIGH(0),
    MEDIUM(1),
    LOW(2);

    private final int sortOrder;

    RecommendationPriority(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int sortOrder() {
        return sortOrder;
    }
}
