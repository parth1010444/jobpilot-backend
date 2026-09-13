package com.jobpilot.analytics.dto;

import java.time.LocalDate;

public record TimelinePoint(
        LocalDate periodStart,
        long applicationsCreated,
        long applicationsApplied
) {
}
