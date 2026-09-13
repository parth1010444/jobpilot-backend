package com.jobpilot.resume.dto;

import com.jobpilot.resume.Resume;
import java.time.Instant;
import java.util.UUID;

public record ResumeResponse(
        UUID id,
        UUID userId,
        String name,
        String versionLabel,
        String description,
        String fileUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getUserId(),
                resume.getName(),
                resume.getVersionLabel(),
                resume.getDescription(),
                resume.getFileUrl(),
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
