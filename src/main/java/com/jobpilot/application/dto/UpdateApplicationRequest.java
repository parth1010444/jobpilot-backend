package com.jobpilot.application.dto;

import com.jobpilot.application.ApplicationSource;
import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.application.EmploymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Partial update. Null fields (other than {@code version}) are left unchanged.
 * {@code version} is required for optimistic locking.
 */
public record UpdateApplicationRequest(
        @NotNull Long version,
        @Size(max = 255) String company,
        @Size(max = 255) String jobTitle,
        @Size(max = 2048) String jobUrl,
        @Size(max = 255) String location,
        EmploymentType employmentType,
        ApplicationSource source,
        ApplicationStatus status,
        Integer salaryMin,
        Integer salaryMax,
        String jobDescription,
        String notes,
        Instant appliedAt
) {
}
