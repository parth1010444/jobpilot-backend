package com.jobpilot.application.dto;

import com.jobpilot.application.Application;
import com.jobpilot.application.ApplicationSource;
import com.jobpilot.application.ApplicationStatus;
import com.jobpilot.application.EmploymentType;
import java.time.Instant;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID userId,
        String company,
        String jobTitle,
        String jobUrl,
        String location,
        EmploymentType employmentType,
        ApplicationSource source,
        ApplicationStatus status,
        Integer salaryMin,
        Integer salaryMax,
        String jobDescription,
        String notes,
        Instant appliedAt,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
    public static ApplicationResponse from(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getUserId(),
                application.getCompany(),
                application.getJobTitle(),
                application.getJobUrl(),
                application.getLocation(),
                application.getEmploymentType(),
                application.getSource(),
                application.getStatus(),
                application.getSalaryMin(),
                application.getSalaryMax(),
                application.getJobDescription(),
                application.getNotes(),
                application.getAppliedAt(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                application.getVersion()
        );
    }
}
