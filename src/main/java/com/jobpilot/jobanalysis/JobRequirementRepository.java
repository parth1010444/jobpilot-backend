package com.jobpilot.jobanalysis;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRequirementRepository extends JpaRepository<JobRequirement, UUID> {

    List<JobRequirement> findByApplicationIdOrderBySkillNameAsc(UUID applicationId);

    void deleteByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);

    List<JobRequirement> findByApplicationIdIn(Collection<UUID> applicationIds);
}

