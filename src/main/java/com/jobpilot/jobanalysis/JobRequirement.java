package com.jobpilot.jobanalysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "job_requirements")
public class JobRequirement {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "skill_name", nullable = false, length = 255)
    private String skillName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JobRequirement() {
    }

    public JobRequirement(UUID applicationId, String skillName) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.skillName = skillName;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getApplicationId() {
        return applicationId;
    }

    public String getSkillName() {
        return skillName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
