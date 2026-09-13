package com.jobpilot.resume.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial update. Null fields are left unchanged.
 */
public record UpdateResumeRequest(
        @Size(max = 255) String name,
        @Size(max = 50) String versionLabel,
        String description,
        @Size(max = 2048) String fileUrl
) {
}
