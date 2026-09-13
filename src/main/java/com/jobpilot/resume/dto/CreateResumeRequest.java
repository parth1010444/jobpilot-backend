package com.jobpilot.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateResumeRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 50) String versionLabel,
        String description,
        @Size(max = 2048) String fileUrl
) {
}
