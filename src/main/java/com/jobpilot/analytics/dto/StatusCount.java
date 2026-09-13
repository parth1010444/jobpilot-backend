package com.jobpilot.analytics.dto;

import com.jobpilot.application.ApplicationStatus;

public record StatusCount(
        ApplicationStatus status,
        long count
) {
}
