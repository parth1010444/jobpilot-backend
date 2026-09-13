package com.jobpilot.jobanalysis.dto;

import jakarta.validation.constraints.NotBlank;

public record PreviewJobAnalysisRequest(
        @NotBlank String jobDescription
) {
}
