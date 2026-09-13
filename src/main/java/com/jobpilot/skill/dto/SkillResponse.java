package com.jobpilot.skill.dto;

import com.jobpilot.skill.Skill;
import java.time.Instant;
import java.util.UUID;

public record SkillResponse(
        UUID id,
        UUID userId,
        String name,
        Instant createdAt
) {
    public static SkillResponse from(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getUserId(),
                skill.getName(),
                skill.getCreatedAt()
        );
    }
}
