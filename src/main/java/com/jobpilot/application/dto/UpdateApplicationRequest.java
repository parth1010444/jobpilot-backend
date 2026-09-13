package com.jobpilot.application.dto;

import com.jobpilot.application.ApplicationSource;
import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.application.EmploymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Partial update. Null fields (other than {@code version}) are left unchanged.
 * {@code version} is required for optimistic locking.
 * {@code resumeId} is {@link java.util.Optional}: omit to leave unchanged,
 * {@code Optional.empty()} / JSON {@code null} to unlink, or a value to attach
 * a resume owned by the same user.
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
        Instant appliedAt,
        Optional<UUID> resumeId
) {
}
