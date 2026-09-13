package com.jobpilot.application.dto;

import com.jobpilot.application.ApplicationSource;
import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.application.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotBlank @Size(max = 255) String company,
        @NotBlank @Size(max = 255) String jobTitle,
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
        UUID resumeId
) {
}
