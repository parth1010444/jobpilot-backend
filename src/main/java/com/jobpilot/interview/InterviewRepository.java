package com.jobpilot.interview;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    Page<Interview> findByApplicationId(UUID applicationId, Pageable pageable);

    List<Interview> findByApplicationId(UUID applicationId);

    List<Interview> findByApplicationIdIn(Collection<UUID> applicationIds);

    Optional<Interview> findByIdAndApplicationUserId(UUID id, UUID userId);

    boolean existsByApplicationIdAndRoundNumber(UUID applicationId, int roundNumber);

    boolean existsByApplicationIdAndRoundNumberAndIdNot(UUID applicationId, int roundNumber, UUID id);
}
